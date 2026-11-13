package com.bukang.order.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.domain.OrderStatus;
import com.bukang.order.domain.Participation;
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
 * [결제하기]의 자리 확보와 그 뒷정리 (BUK-213)
 * - 상품에 자리를 잡는 동안 참가 신청을 잠그지 않는다
 * - 자리를 잡은 뒤 주문을 만들지 못한 신청서는 기한 뒤 만료 정리가 자리를 풀어 준다
 * - 만료 정리는 한 번에 읽는 수(100)보다 많아도 끝까지 처리하고, 예치금 장애가 이어지면 멈춘다
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubSeatHoldPortConfig.class, StubWalletPortConfig.class})
class OrderHoldCleanupTest {
	private static final long MEMBER_ID = 1;
	private static final long COURSE_ID = 10;
	private static final long LOCK_WAIT_SECONDS = 2;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private ParticipationRepository participationRepository;

	@Autowired
	private QueueAdmissionRepository queueAdmissionRepository;

	@Autowired
	private OrderOutboxKafkaEventRepository orderOutboxKafkaEventRepository;

	@Autowired
	private OrderExpiryUseCase orderExpiryUseCase;

	@Autowired
	private StubRaceCoursePort raceCoursePort;

	@Autowired
	private StubSeatHoldPort seatHoldPort;

	@Autowired
	private StubWalletPort walletPort;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@BeforeEach
	void setUp() {
		orderOutboxKafkaEventRepository.deleteAll();
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID, 100, 14, null));
	}

	@AfterEach
	void tearDown() {
		orderRepository.deleteAll();
		participationRepository.deleteAll();
		queueAdmissionRepository.deleteAll();
		orderOutboxKafkaEventRepository.deleteAll();
		raceCoursePort.clear();
		seatHoldPort.clear();
		walletPort.clear();
	}

	@Test
	@DisplayName("상품에 자리를 잡는 동안 참가 신청을 잠그지 않는다 (다른 요청이 그 신청서를 바로 잠글 수 있다)")
	void holdsSeatWithoutLock() throws Exception {
		long participationId = apply(MEMBER_ID);
		AtomicBoolean lockedDuringHold = new AtomicBoolean();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			seatHoldPort.afterHold(() -> lockedDuringHold.set(tryLock(executor, participationId)));

			order(MEMBER_ID, participationId).andExpect(status().isCreated());
		} finally {
			executor.shutdownNow();
		}

		assertThat(lockedDuringHold).isTrue();
	}

	@Test
	@DisplayName("자리를 잡은 뒤 답을 잃어 주문을 못 만들면 신청서는 확보 전으로 남고, 기한이 지나면 만료 정리가 자리를 한 번 풀고 미결제 해제로 끝낸다")
	void releasesSeatHeldWithoutOrder() throws Exception {
		long participationId = apply(MEMBER_ID);
		seatHoldPort.afterHold(() -> {
			throw new ExternalServiceUnavailableException("상품 서비스 응답 시간 초과 (테스트)");
		});

		order(MEMBER_ID, participationId).andExpect(status().isServiceUnavailable());
		// 표시는 화면에 결제 기한으로 내보내지 않는다
		assertThat(participationRepository.findById(participationId).orElseThrow().toDto().getPaymentDueAt()).isNull();

		Participation pending = participationRepository.findById(participationId).orElseThrow();
		assertThat(pending.getStatus()).isEqualTo(ParticipationStatus.PENDING);
		assertThat(pending.getPaymentDueAt()).isNotNull();
		assertThat(orderRepository.count()).isZero();
		LocalDateTime due = pending.getPaymentDueAt();

		// 기한 전에는 손대지 않는다 (같은 신청서로 다시 [결제하기]할 수 있다)
		assertThat(orderExpiryUseCase.releaseAbandonedHolds(due.minusSeconds(1))).isZero();
		assertThat(released()).isEmpty();

		assertThat(orderExpiryUseCase.releaseAbandonedHolds(due)).isEqualTo(1);
		assertThat(orderExpiryUseCase.releaseAbandonedHolds(due.plusMinutes(1))).isZero();

		Participation done = participationRepository.findById(participationId).orElseThrow();
		assertThat(done.getStatus()).isEqualTo(ParticipationStatus.RELEASED);
		assertThat(done.getActiveFlag()).isNull();
		assertThat(released()).containsExactly(participationId);
	}

	@Test
	@DisplayName("자리를 잡는 사이 회원이 신청서를 취소하면 주문 없이 409이고, 잡힌 자리를 풀어 달라는 메시지를 다시 남긴다")
	void releasesSeatWhenCanceledDuringHold() throws Exception {
		long participationId = apply(MEMBER_ID);
		seatHoldPort.afterHold(() -> {
			try {
				mockMvc.perform(post("/api/v1/order/participations/" + participationId + "/cancel")
						.header("X-Member-Id", MEMBER_ID))
					.andExpect(status().isOk());
			} catch (Exception exception) {
				throw new IllegalStateException(exception);
			}
		});

		order(MEMBER_ID, participationId).andExpect(status().isConflict());

		assertThat(orderRepository.count()).isZero();
		assertThat(participationRepository.findById(participationId).orElseThrow().getStatus())
			.isEqualTo(ParticipationStatus.CANCELED);
		// 취소가 남긴 해제 + 확보 뒤 다시 남긴 해제 (상품의 해제는 참가 신청 ID로 멱등)
		assertThat(released()).containsExactly(participationId, participationId);
	}

	@Test
	@DisplayName("자리를 잡으러 가지 않은 확보 전 신청서와 주문을 만든 신청서는 자리 해제 대상이 아니다")
	void keepsPendingWithoutHoldAttempt() throws Exception {
		long untouched = apply(MEMBER_ID);
		long ordered = apply(2);
		order(2, ordered).andExpect(status().isCreated());

		assertThat(orderExpiryUseCase.releaseAbandonedHolds(LocalDateTime.now().plusDays(1))).isZero();

		assertThat(participationRepository.findById(untouched).orElseThrow().getStatus())
			.isEqualTo(ParticipationStatus.PENDING);
		assertThat(participationRepository.findById(ordered).orElseThrow().getStatus())
			.isEqualTo(ParticipationStatus.APPLIED);
		assertThat(released()).isEmpty();
	}

	@Test
	@DisplayName("결제 기한이 지난 주문이 한 번에 읽는 수보다 많아도 끝까지 만료하고, 한 묶음이 모두 실패하면(예치금 장애) 멈춘다")
	void expiresBeyondOneBatch() throws Exception {
		int count = OrderExpiryUseCase.BATCH_SIZE + 1;
		for (long memberId = 1; memberId <= count; memberId++) {
			order(memberId, apply(memberId)).andExpect(status().isCreated());
		}
		LocalDateTime afterDue = LocalDateTime.now().plusMinutes(OrderCreateUseCase.MAX_ADMISSION_MINUTES + 1);

		walletPort.setCancelFails(true);
		assertThat(orderExpiryUseCase.expireOverdue(afterDue))
			.isEqualTo(new OrderExpiryUseCase.ExpiryResult(0, 0, OrderExpiryUseCase.BATCH_SIZE));

		walletPort.setCancelFails(false);
		assertThat(orderExpiryUseCase.expireOverdue(afterDue))
			.isEqualTo(new OrderExpiryUseCase.ExpiryResult(count, count, 0));
		assertThat(orderRepository.findAll()).allSatisfy(
			order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED));
	}

	// 다른 스레드에서 신청서를 잠가 본다. 잠금을 쥔 요청이 있으면 그 요청이 끝날 때까지 기다리므로 시간 안에 못 잠근다
	private boolean tryLock(ExecutorService executor, long participationId) {
		Future<?> lock = executor.submit(() -> transactionTemplate.executeWithoutResult(
			status -> participationRepository.findByIdForUpdate(participationId)));
		try {
			lock.get(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
			return true;
		} catch (TimeoutException exception) {
			lock.cancel(true);
			return false;
		} catch (Exception exception) {
			return false;
		}
	}

	private List<Long> released() {
		return SeatOutboxMessages.released(orderOutboxKafkaEventRepository);
	}

	private ResultActions order(long memberId, long participationId) throws Exception {
		return mockMvc.perform(post("/api/v1/order/orders")
			.header("X-Member-Id", memberId)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"participationId\": %d}".formatted(participationId)));
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
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(body, "$.data.id").longValue();
	}
}
