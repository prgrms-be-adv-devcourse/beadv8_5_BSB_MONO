package com.bukang.order.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.domain.Order;
import com.bukang.order.domain.OrderStatus;
import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.out.OrderOutboxKafkaEventRepository;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.QueueAdmissionRepository;
import com.bukang.order.support.QueueEntry;
import com.bukang.order.support.SeatOutboxMessages;
import com.bukang.order.support.StubRaceCoursePort;
import com.bukang.order.support.StubRaceCoursePortConfig;
import com.bukang.order.support.StubSeatHoldPort;
import com.bukang.order.support.StubSeatHoldPortConfig;
import com.bukang.order.support.StubWalletPort;
import com.bukang.order.support.StubWalletPortConfig;
import com.jayway.jsonpath.JsonPath;

/**
 * 예치금(D)과 주문의 트랜잭션이 따로라 생기는 "돈만 빠진 결제 대기 주문"을 만료 정리가 결제 취소로 돌려받는지 본다 (PRO-138)
 * 응답 시간 초과는 스텁이 차감까지 끝낸 뒤 예외를 던져 흉내 낸다 (D는 처리했는데 주문은 응답을 못 받은 경우)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubSeatHoldPortConfig.class, StubWalletPortConfig.class})
class OrderExpiryWalletTest {
	private static final long COURSE_ID = 20;
	private static final long RACE_ID = 200;
	private static final long MEMBER_ID = 7;
	private static final long OTHER_MEMBER_ID = 8;
	private static final long WAIT_SECONDS = 30;
	// 만료 정리가 주문 잠금에서 기다리기 시작할 만큼의 시간. H2 조회는 이보다 훨씬 빨리 끝난다
	private static final long LOCK_WAIT_MILLIS = 500;
	private static final long BALANCE = 1_000_000;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OrderRepository orderRepository;

	// 만료 뒤 같은 트랜잭션에서 실패하는 경우를 만들려고 참가 신청 잠금 조회만 바꾼다 (나머지는 실제 동작)
	@MockitoSpyBean
	private ParticipationRepository participationRepository;

	@Autowired
	private QueueAdmissionRepository queueAdmissionRepository;

	@Autowired
	private OrderCreateUseCase orderCreateUseCase;

	@Autowired
	private OrderPayUseCase orderPayUseCase;

	@Autowired
	private OrderExpiryUseCase orderExpiryUseCase;

	@Autowired
	private StubRaceCoursePort raceCoursePort;

	@Autowired
	private StubSeatHoldPort seatHoldPort;

	@Autowired
	private OrderOutboxKafkaEventRepository orderOutboxKafkaEventRepository;

	@Autowired
	private StubWalletPort walletPort;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@BeforeEach
	void setUp() {
		orderOutboxKafkaEventRepository.deleteAll();
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null));
		walletPort.setBalance(BALANCE);
	}

	@AfterEach
	void tearDown() {
		orderRepository.deleteAll();
		participationRepository.deleteAll();
		queueAdmissionRepository.deleteAll();
		raceCoursePort.clear();
		seatHoldPort.clear();
		orderOutboxKafkaEventRepository.deleteAll();
		walletPort.clear();
	}

	@Test
	@DisplayName("결제 응답 시간 초과는 실패로 끝내지 않는다: 주문은 결제 대기로 남고 결제 취소는 부르지 않으며, 같은 주문으로 다시 결제하면 한 번만 차감된다")
	void payTimeoutKeepsOrderPayable() throws Exception {
		long orderId = createOrder();
		walletPort.setPayTimesOut(true);

		assertThatThrownBy(() -> pay(orderId)).isInstanceOf(ExternalServiceUnavailableException.class);

		assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CREATED);
		assertThat(participationStatusOf(orderId)).isEqualTo(ParticipationStatus.APPLIED);
		assertThat(walletPort.cancelCalls()).isEmpty();
		// D는 이미 차감했다
		assertThat(walletPort.chargedOrders()).containsExactly(orderId);

		walletPort.setPayTimesOut(false);
		pay(orderId);

		assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PAID);
		assertThat(walletPort.payCalls()).hasSize(2);
		assertThat(walletPort.balance()).isEqualTo(BALANCE - StubRaceCoursePort.PRICE);
		assertThat(walletPort.cancelCalls()).isEmpty();
	}

	@Test
	@DisplayName("돈만 빠진 채 결제 대기로 남은 주문은 기한이 지나면 만료 정리가 주문 ID·회원 ID로 결제를 취소해 돌려준 뒤 만료한다")
	void expiryCancelsChargedPayment() throws Exception {
		long orderId = createOrder();
		walletPort.setPayTimesOut(true);
		assertThatThrownBy(() -> pay(orderId)).isInstanceOf(ExternalServiceUnavailableException.class);
		assertThat(walletPort.balance()).isEqualTo(BALANCE - StubRaceCoursePort.PRICE);

		OrderExpiryUseCase.ExpiryResult result = orderExpiryUseCase.expireOverdue(dueOf(orderId));

		assertThat(result).isEqualTo(new OrderExpiryUseCase.ExpiryResult(1, 1, 0));
		assertThat(walletPort.cancelCalls()).containsExactly(new StubWalletPort.Call(orderId, MEMBER_ID));
		assertThat(walletPort.balance()).isEqualTo(BALANCE);
		assertThat(walletPort.chargedOrders()).isEmpty();
		assertThat(statusOf(orderId)).isEqualTo(OrderStatus.EXPIRED);
		assertThat(participationStatusOf(orderId)).isEqualTo(ParticipationStatus.RELEASED);
		// 판매된 채 남았을 수 있는 자리는 복구하고, 결제하지 않은 자리는 풀어 달라고 만료 트랜잭션 안에서 아웃박스에 남긴다 (PRO-270)
		long participationId = participationIdOf(orderId);
		assertThat(restoredSeats()).containsExactly(participationId);
		assertThat(releasedSeats()).containsExactly(participationId);
	}

	@Test
	@DisplayName("결제 취소가 실패한 주문은 만료하지 않고 결제 대기로 남겨, 다음 실행에서 다시 결제 취소를 부른 뒤 만료한다")
	void expiryRetriesWhenCancelFails() throws Exception {
		long orderId = createOrder();
		LocalDateTime due = dueOf(orderId);
		walletPort.setCancelFails(true);

		OrderExpiryUseCase.ExpiryResult first = orderExpiryUseCase.expireOverdue(due);

		assertThat(first).isEqualTo(new OrderExpiryUseCase.ExpiryResult(0, 0, 1));
		assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CREATED);
		assertThat(participationStatusOf(orderId)).isEqualTo(ParticipationStatus.APPLIED);
		// 결제 취소가 실패해 만료가 롤백됐으니 자리 메시지도 남지 않는다 (만료 트랜잭션 안에서 아웃박스에 남긴다)
		assertThat(releasedSeats()).isEmpty();

		walletPort.setCancelFails(false);
		OrderExpiryUseCase.ExpiryResult second = orderExpiryUseCase.expireOverdue(due.plusMinutes(1));

		assertThat(second).isEqualTo(new OrderExpiryUseCase.ExpiryResult(1, 1, 0));
		assertThat(walletPort.cancelCalls()).containsExactly(
			new StubWalletPort.Call(orderId, MEMBER_ID), new StubWalletPort.Call(orderId, MEMBER_ID));
		assertThat(statusOf(orderId)).isEqualTo(OrderStatus.EXPIRED);
		assertThat(participationStatusOf(orderId)).isEqualTo(ParticipationStatus.RELEASED);
		assertThat(releasedSeats()).containsExactly(participationIdOf(orderId));
	}

	@Test
	@DisplayName("자리 메시지를 남긴 뒤 같은 트랜잭션에서 실패하면(참가 신청 잠금 실패) 만료와 함께 메시지도 롤백되고, "
		+ "다음 실행에서 만료하면 복구·해제 메시지가 한 번씩만 남는다")
	void seatMessagesRollBackWithExpiry() throws Exception {
		long orderId = createOrder();
		long participationId = participationIdOf(orderId);
		doThrow(new IllegalStateException("잠금 대기 시간 초과 (테스트)"))
			.when(participationRepository).findByIdForUpdate(participationId);

		OrderExpiryUseCase.ExpiryResult result = orderExpiryUseCase.expireOverdue(dueOf(orderId));

		assertThat(result).isEqualTo(new OrderExpiryUseCase.ExpiryResult(0, 0, 1));
		assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CREATED);
		assertThat(restoredSeats()).isEmpty();
		assertThat(releasedSeats()).isEmpty();

		reset(participationRepository);
		orderExpiryUseCase.expireOverdue(dueOf(orderId).plusMinutes(1));

		assertThat(statusOf(orderId)).isEqualTo(OrderStatus.EXPIRED);
		assertThat(restoredSeats()).containsExactly(participationId);
		assertThat(releasedSeats()).containsExactly(participationId);
	}

	@Test
	@DisplayName("기한 전 주문과 이미 결제한 주문에는 결제 취소를 부르지 않는다")
	void expirySkipsNotOverdueAndPaid() throws Exception {
		long waitingId = createOrder(MEMBER_ID);
		long paidId = createOrder(OTHER_MEMBER_ID);
		pay(OTHER_MEMBER_ID, paidId, LocalDateTime.now());

		OrderExpiryUseCase.ExpiryResult result = orderExpiryUseCase.expireOverdue(dueOf(waitingId).minusSeconds(1));

		assertThat(result).isEqualTo(new OrderExpiryUseCase.ExpiryResult(0, 0, 0));
		assertThat(statusOf(waitingId)).isEqualTo(OrderStatus.CREATED);
		assertThat(statusOf(paidId)).isEqualTo(OrderStatus.PAID);
		assertThat(walletPort.cancelCalls()).isEmpty();
		assertThat(walletPort.chargedOrders()).containsExactly(paidId);
	}

	@Test
	@DisplayName("결제가 주문을 잠근 채 차감하는 동안 만료 정리가 그 주문을 읽어도, 잠금을 기다린 뒤 결제된 주문이라 건너뛰고 결제 취소를 부르지 않는다")
	void expiryWaitsForPayInProgress() throws Exception {
		long orderId = createOrder(MEMBER_ID);
		LocalDateTime due = dueOf(orderId);
		CountDownLatch payEntered = walletPort.holdPay();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			// 기한 1밀리초 전에 결제를 시작해 D 차감 직전에 멈춘다 (주문 잠금을 쥔 채)
			Future<OrderPayResult> payment = executor.submit(() -> pay(MEMBER_ID, orderId, due.minusNanos(1_000_000)));
			assertThat(payEntered.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
			// 결제는 아직 커밋 전이라 만료 정리는 이 주문을 결제 대기로 읽고, 주문 잠금에서 기다린다
			Future<OrderExpiryUseCase.ExpiryResult> expiry = executor.submit(
				() -> orderExpiryUseCase.expireOverdue(due));
			Thread.sleep(LOCK_WAIT_MILLIS);
			assertThat(expiry.isDone()).isFalse();
			walletPort.releasePay();

			assertThat(payment.get(WAIT_SECONDS, TimeUnit.SECONDS).order().getStatus()).isEqualTo(OrderStatus.PAID);
			assertThat(expiry.get(WAIT_SECONDS, TimeUnit.SECONDS))
				.isEqualTo(new OrderExpiryUseCase.ExpiryResult(0, 0, 0));
		} finally {
			walletPort.releasePay();
			executor.shutdownNow();
		}
		assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PAID);
		assertThat(walletPort.cancelCalls()).isEmpty();
		assertThat(walletPort.chargedOrders()).containsExactly(orderId);
	}

	// 신청서 작성 → [결제하기]로 결제 대기 주문을 만든다
	private long createOrder() throws Exception {
		return createOrder(MEMBER_ID);
	}

	private long createOrder(long memberId) throws Exception {
		long participationId = apply(memberId);
		OrderCreateResult result = transactionTemplate.execute(status -> orderCreateUseCase.create(memberId,
			participationId, UUID.randomUUID().toString(), LocalDateTime.now()));
		return result.order().getId();
	}

	// 컨트롤러와 같이 한 트랜잭션에서 결제한다 (OrderFacade.pay)
	private OrderPayResult pay(long orderId) {
		return pay(MEMBER_ID, orderId, LocalDateTime.now());
	}

	private OrderPayResult pay(long memberId, long orderId, LocalDateTime now) {
		return transactionTemplate.execute(status -> orderPayUseCase.pay(memberId, orderId, now));
	}

	private long apply(long memberId) throws Exception {
		QueueEntry.enter(mockMvc, memberId, COURSE_ID);
		String body = mockMvc.perform(post("/api/v1/order/participations")
				.header("X-Member-Id", memberId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "raceCourseId": %d, "thirdPartyConsent": true,
						"participant": { "name": "김러너", "birth": "1994-06-25", "gender": "MALE",
							"phone": "010-1234-5678", "emergencyContactName": "김가족",
							"emergencyContactPhone": "010-9876-5432", "tshirtSize": "L",
						"shippingAddress": { "recipientName": "김러너", "recipientPhone": "010-1234-5678",
							"zipcode": "04700", "address": "서울 성동구 왕십리로 00", "addressDetail": "101동 1001호" } } }
					""".formatted(COURSE_ID)))
			.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(body, "$.data.id").longValue();
	}

	private Order orderOf(long orderId) {
		return orderRepository.findById(orderId).orElseThrow();
	}

	private OrderStatus statusOf(long orderId) {
		return orderOf(orderId).getStatus();
	}

	// DB에 저장된 기한 (저장하며 잘린 정밀도 그대로)
	private LocalDateTime dueOf(long orderId) {
		return orderOf(orderId).getPaymentDueAt();
	}

	private long participationIdOf(long orderId) {
		// 주문 상품은 지연 로딩이라 트랜잭션 안에서 읽는다
		return transactionTemplate.execute(status -> orderOf(orderId).participationId());
	}

	private ParticipationStatus participationStatusOf(long orderId) {
		// 주문 상품은 지연 로딩이라 트랜잭션 안에서 읽는다
		long participationId = transactionTemplate.execute(status -> orderOf(orderId).participationId());
		return participationRepository.findById(participationId).orElseThrow().getStatus();
	}

	private List<Long> restoredSeats() {
		return SeatOutboxMessages.restored(orderOutboxKafkaEventRepository);
	}

	private List<Long> releasedSeats() {
		return SeatOutboxMessages.released(orderOutboxKafkaEventRepository);
	}
}
