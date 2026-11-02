package com.bukang.order.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import com.bukang.order.domain.OrderItemStatus;
import com.bukang.order.domain.OrderStatus;
import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.domain.event.OrderRaceCanceledEvent;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.domain.exception.OrderNotPayableException;
import com.bukang.order.in.RaceCanceledNotificationListener;
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
 * 대회가 취소되면 그 대회의 결제한 참가권을 모두 취소·전액 환불하는지 본다 (PRO-135)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubSeatHoldPortConfig.class, StubWalletPortConfig.class})
class RaceCancelRefundTest {
	private static final long COURSE_ID = 30;
	private static final long RACE_ID = 300;
	private static final long OTHER_COURSE_ID = 31;
	private static final long OTHER_RACE_ID = 301;
	private static final long WAIT_SECONDS = 30;
	// 환불이 주문 잠금에서 기다리기 시작할 만큼의 시간. H2 조회는 이보다 훨씬 빨리 끝난다
	private static final long LOCK_WAIT_MILLIS = 500;
	// 테스트용 예치금은 모든 회원이 잔액 하나를 함께 쓴다. 주문을 많이 만들어도 모자라지 않게 넉넉히 둔다
	private static final long BALANCE = 100_000_000;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private ParticipationRepository participationRepository;

	@Autowired
	private QueueAdmissionRepository queueAdmissionRepository;

	@Autowired
	private OrderCreateUseCase orderCreateUseCase;

	@Autowired
	private OrderPayUseCase orderPayUseCase;

	@Autowired
	private RaceCancelRefundUseCase raceCancelRefundUseCase;

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

	@MockitoSpyBean
	private RaceCanceledNotificationListener notificationListener;

	@BeforeEach
	void setUp() {
		orderOutboxKafkaEventRepository.deleteAll();
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null));
		raceCoursePort.put(StubRaceCoursePort.openCourse(OTHER_COURSE_ID, OTHER_RACE_ID, 14, null));
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
	@DisplayName("취소된 대회의 결제한 주문만 주문·주문 상품·참가 신청을 취소하고 구매자에게 주문 단위로 전액 환불한다")
	void refundsPaidOrdersOfCanceledRace() throws Exception {
		long first = paidOrder(1, COURSE_ID);
		long second = paidOrder(2, COURSE_ID);
		long waiting = createOrder(3, COURSE_ID);
		long otherRace = paidOrder(4, OTHER_COURSE_ID);
		LocalDateTime now = LocalDateTime.now();

		RaceCancelRefundUseCase.RefundResult result = raceCancelRefundUseCase.refundAll(RACE_ID, now);

		assertThat(result).isEqualTo(new RaceCancelRefundUseCase.RefundResult(2, 0));
		assertCanceled(first);
		assertCanceled(second);
		assertThat(walletPort.refundCalls())
			.containsExactlyInAnyOrder(new StubWalletPort.Call(first, 1), new StubWalletPort.Call(second, 2));
		assertThat(walletPort.refundedAmounts()).containsExactly(StubRaceCoursePort.PRICE, StubRaceCoursePort.PRICE);
		// 결제 대기 주문은 결제를 막아 두고(만료 정리가 끝낸다), 다른 대회 주문은 그대로 둔다
		assertThat(statusOf(waiting)).isEqualTo(OrderStatus.CREATED);
		assertThat(walletPort.cancelCalls()).containsExactly(new StubWalletPort.Call(waiting, 3));
		assertThat(statusOf(otherRace)).isEqualTo(OrderStatus.PAID);
		// 자리는 되돌리지 않는다 (취소된 대회는 다시 팔지 않는다)
		assertThat(restoredSeats()).isEmpty();
		// 환불한 구매자에게만 알린다
		verify(notificationListener)
			.notifyBuyer(new OrderRaceCanceledEvent(first, 1, RACE_ID, StubRaceCoursePort.PRICE));
		verify(notificationListener)
			.notifyBuyer(new OrderRaceCanceledEvent(second, 2, RACE_ID, StubRaceCoursePort.PRICE));
		verify(notificationListener, times(2)).notifyBuyer(any());
	}

	@Test
	@DisplayName("같은 대회 취소가 다시 와도 이미 환불한 주문은 건너뛰어 한 번만 환불한다")
	void refundIsIdempotent() throws Exception {
		long orderId = paidOrder(1, COURSE_ID);
		raceCancelRefundUseCase.refundAll(RACE_ID, LocalDateTime.now());

		RaceCancelRefundUseCase.RefundResult again = raceCancelRefundUseCase.refundAll(RACE_ID, LocalDateTime.now());

		assertThat(again).isEqualTo(new RaceCancelRefundUseCase.RefundResult(0, 0));
		assertThat(walletPort.refundCalls()).containsExactly(new StubWalletPort.Call(orderId, 1));
	}

	@Test
	@DisplayName("환불이 실패한 주문만 결제 완료로 남고 나머지는 환불되며, 다시 처리하면 남은 주문만 환불한다")
	void failedRefundIsRetried() throws Exception {
		long first = paidOrder(1, COURSE_ID);
		walletPort.setRefundFails(true);

		RaceCancelRefundUseCase.RefundResult failed = raceCancelRefundUseCase.refundAll(RACE_ID, LocalDateTime.now());

		assertThat(failed).isEqualTo(new RaceCancelRefundUseCase.RefundResult(0, 1));
		assertThat(statusOf(first)).isEqualTo(OrderStatus.PAID);
		assertThat(participationStatusOf(first)).isEqualTo(ParticipationStatus.CONFIRMED);
		// 환불이 롤백된 주문은 알리지 않는다
		verify(notificationListener, never()).notifyBuyer(any());

		walletPort.setRefundFails(false);
		long second = paidOrder(2, COURSE_ID);
		RaceCancelRefundUseCase.RefundResult retried = raceCancelRefundUseCase.refundAll(RACE_ID, LocalDateTime.now());

		assertThat(retried).isEqualTo(new RaceCancelRefundUseCase.RefundResult(2, 0));
		assertCanceled(first);
		assertCanceled(second);
		assertThat(walletPort.refundedAmounts()).hasSize(2);
	}

	@Test
	@DisplayName("환불이 연달아 실패하면(예치금 서비스 장애) 남은 주문을 부르지 않고 멈춰, 재시도에 맡긴다")
	void stopsAfterConsecutiveFailures() throws Exception {
		int count = RaceCancelRefundUseCase.MAX_CONSECUTIVE_FAILURES + 2;
		for (long memberId = 1; memberId <= count; memberId++) {
			paidOrder(memberId, COURSE_ID);
		}
		walletPort.setRefundFails(true);

		RaceCancelRefundUseCase.RefundResult result = raceCancelRefundUseCase.refundAll(RACE_ID, LocalDateTime.now());

		assertThat(result).isEqualTo(
			new RaceCancelRefundUseCase.RefundResult(0, RaceCancelRefundUseCase.MAX_CONSECUTIVE_FAILURES));
		assertThat(walletPort.refundCalls()).hasSize(RaceCancelRefundUseCase.MAX_CONSECUTIVE_FAILURES);
	}

	@Test
	@DisplayName("결제 응답을 못 받아 결제 대기로 남은 주문은 결제를 취소해 돈을 돌려받고, 대회 취소 뒤 다시 결제해도 거절된다")
	void blocksLatePaymentOfWaitingOrder() throws Exception {
		long orderId = createOrder(1, COURSE_ID);
		walletPort.setPayTimesOut(true);
		assertThatThrownBy(() -> pay(1, orderId)).isInstanceOf(ExternalServiceUnavailableException.class);
		walletPort.setPayTimesOut(false);
		assertThat(walletPort.balance()).isEqualTo(BALANCE - StubRaceCoursePort.PRICE);

		raceCancelRefundUseCase.refundAll(RACE_ID, LocalDateTime.now());

		assertThat(walletPort.balance()).isEqualTo(BALANCE);
		assertThatThrownBy(() -> pay(1, orderId)).isInstanceOf(OrderNotPayableException.class);
		assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CREATED);
		verify(notificationListener, never()).notifyBuyer(any());
	}

	@Test
	@DisplayName("다시 해도 안 되는 실패(D 거절 등)는 그 주문만 실패로 세고, 연달아 나도 멈추지 않고 나머지를 환불한다")
	void keepsGoingAfterRejectedRefunds() throws Exception {
		int count = RaceCancelRefundUseCase.MAX_CONSECUTIVE_FAILURES + 2;
		for (long memberId = 1; memberId <= count; memberId++) {
			paidOrder(memberId, COURSE_ID);
		}
		walletPort.setRefundRejects(true);

		RaceCancelRefundUseCase.RefundResult result = raceCancelRefundUseCase.refundAll(RACE_ID, LocalDateTime.now());

		assertThat(result).isEqualTo(new RaceCancelRefundUseCase.RefundResult(0, count));
		assertThat(walletPort.refundCalls()).hasSize(count);
	}

	@Test
	@DisplayName("주문이 많아도 끝까지 읽어 모두 환불한다 (한 번에 읽는 수보다 많을 때)")
	void refundsBeyondOneBatch() throws Exception {
		int count = RaceCancelRefundUseCase.BATCH_SIZE + 1;
		for (long memberId = 1; memberId <= count; memberId++) {
			paidOrder(memberId, COURSE_ID);
		}

		RaceCancelRefundUseCase.RefundResult result = raceCancelRefundUseCase.refundAll(RACE_ID, LocalDateTime.now());

		assertThat(result).isEqualTo(new RaceCancelRefundUseCase.RefundResult(count, 0));
		assertThat(walletPort.refundCalls()).hasSize(count);
	}

	@Test
	@DisplayName("결제가 주문을 잠근 채 끝나 가는 중에 대회 취소가 오면, 결제가 끝나길 기다렸다가 그 주문도 환불한다")
	void refundWaitsForPaymentInProgress() throws Exception {
		long orderId = createOrder(1, COURSE_ID);
		CountDownLatch payEntered = walletPort.holdPay();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<OrderPayResult> payment = executor.submit(() -> pay(1, orderId));
			assertThat(payEntered.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

			Future<RaceCancelRefundUseCase.RefundResult> refund =
				executor.submit(() -> raceCancelRefundUseCase.refundAll(RACE_ID, LocalDateTime.now()));
			Thread.sleep(LOCK_WAIT_MILLIS);
			assertThat(refund.isDone()).isFalse();

			walletPort.releasePay();
			payment.get(WAIT_SECONDS, TimeUnit.SECONDS);

			assertThat(refund.get(WAIT_SECONDS, TimeUnit.SECONDS))
				.isEqualTo(new RaceCancelRefundUseCase.RefundResult(1, 0));
		} finally {
			executor.shutdownNow();
		}
		assertCanceled(orderId);
		assertThat(walletPort.balance()).isEqualTo(BALANCE);
	}

	private void assertCanceled(long orderId) {
		Order order = orderOf(orderId);
		assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
		assertThat(order.getCanceledAmount()).isEqualTo(StubRaceCoursePort.PRICE);
		assertThat(order.getItems())
			.allSatisfy(item -> assertThat(item.getStatus()).isEqualTo(OrderItemStatus.CANCELED));
		assertThat(participationStatusOf(orderId)).isEqualTo(ParticipationStatus.CANCELED);
	}

	private long paidOrder(long memberId, long courseId) throws Exception {
		long orderId = createOrder(memberId, courseId);
		pay(memberId, orderId);
		assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PAID);
		return orderId;
	}

	private long createOrder(long memberId, long courseId) throws Exception {
		long participationId = apply(memberId, courseId);
		OrderCreateResult result = transactionTemplate.execute(status -> orderCreateUseCase.create(memberId,
			participationId, UUID.randomUUID().toString(), LocalDateTime.now()));
		return result.order().getId();
	}

	private OrderPayResult pay(long memberId, long orderId) {
		return transactionTemplate.execute(status -> orderPayUseCase.pay(memberId, orderId, LocalDateTime.now()));
	}

	private long apply(long memberId, long courseId) throws Exception {
		QueueEntry.enter(mockMvc, memberId, courseId);
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
					""".formatted(courseId)))
			.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(body, "$.data.id").longValue();
	}

	private Order orderOf(long orderId) {
		return transactionTemplate.execute(status -> {
			Order order = orderRepository.findById(orderId).orElseThrow();
			order.getItems().size();
			return order;
		});
	}

	private OrderStatus statusOf(long orderId) {
		return orderRepository.findById(orderId).orElseThrow().getStatus();
	}

	private ParticipationStatus participationStatusOf(long orderId) {
		long participationId = orderOf(orderId).participationId();
		return participationRepository.findById(participationId).orElseThrow().getStatus();
	}

	private List<Long> restoredSeats() {
		return SeatOutboxMessages.restored(orderOutboxKafkaEventRepository);
	}

	private List<Long> releasedSeats() {
		return SeatOutboxMessages.released(orderOutboxKafkaEventRepository);
	}
}
