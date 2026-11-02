package com.bukang.order.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.app.OrderExpiryUseCase;
import com.bukang.order.app.OrderPayUseCase;
import com.bukang.order.domain.Order;
import com.bukang.order.domain.OrderItemStatus;
import com.bukang.order.domain.OrderStatus;
import com.bukang.order.domain.Participation;
import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.QueueAdmissionRepository;
import com.bukang.order.support.QueueEntry;
import com.bukang.order.support.StubRaceCoursePort;
import com.bukang.order.support.StubRaceCoursePortConfig;
import com.bukang.order.support.StubSeatHoldPort;
import com.bukang.order.support.StubSeatHoldPortConfig;
import com.bukang.order.support.StubWalletPort;
import com.bukang.order.support.StubWalletPortConfig;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubSeatHoldPortConfig.class, StubWalletPortConfig.class})
class ApiV1OrderPaymentTest {
	private static final String BASE_URL = "/api/v1/order/orders";
	private static final long MEMBER_ID = 1;
	private static final long OTHER_MEMBER_ID = 2;
	private static final long COURSE_ID = 10;
	private static final long TICKET_PRICE = StubRaceCoursePort.PRICE;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private ParticipationRepository participationRepository;

	@Autowired
	private QueueAdmissionRepository queueAdmissionRepository;

	@Autowired
	private StubRaceCoursePort raceCoursePort;

	@Autowired
	private StubSeatHoldPort seatHoldPort;

	@Autowired
	private StubWalletPort walletPort;

	@Autowired
	private OrderExpiryUseCase orderExpiryUseCase;

	@Autowired
	private OrderPayUseCase orderPayUseCase;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@BeforeEach
	void setUp() {
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID, 100, 14, null));
	}

	@AfterEach
	void tearDown() {
		orderRepository.deleteAll();
		participationRepository.deleteAll();
		queueAdmissionRepository.deleteAll();
		raceCoursePort.clear();
		seatHoldPort.clear();
		walletPort.clear();
	}

	@Test
	@DisplayName("결제하면 예치금이 차감되고 주문·주문 상품은 PAID, 참가 신청은 CONFIRMED가 된다")
	void pay() throws Exception {
		long orderId = orderAndGetId(MEMBER_ID);

		pay(MEMBER_ID, orderId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("결제했습니다. 참가가 확정되었습니다."))
			.andExpect(jsonPath("$.data.status").value("PAID"))
			.andExpect(jsonPath("$.data.paidAt").isNotEmpty())
			.andExpect(jsonPath("$.data.items[0].status").value("PAID"));

		assertThat(walletPort.payCalls()).containsExactly(TICKET_PRICE);
		Order order = orderRepository.findById(orderId).orElseThrow();
		assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
		Participation participation = participationOf(orderId);
		assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.CONFIRMED);
		assertThat(participation.getConfirmedAt()).isNotNull();
		assertThat(participation.getActiveFlag()).isTrue();
		// 주문 상품은 지연 로딩이라 트랜잭션 안에서 읽는다
		OrderItemStatus itemStatus = transactionTemplate.execute(status ->
			orderRepository.findById(orderId).orElseThrow().getItems().getFirst().getStatus());
		assertThat(itemStatus).isEqualTo(OrderItemStatus.PAID);
	}

	@Test
	@DisplayName("같은 주문에 결제를 두 번 보내면 두 번째는 차감 없이 200으로 결제한 주문을 돌려준다")
	void payTwice() throws Exception {
		long orderId = orderAndGetId(MEMBER_ID);
		pay(MEMBER_ID, orderId).andExpect(status().isOk());

		pay(MEMBER_ID, orderId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("이미 결제한 주문입니다."))
			.andExpect(jsonPath("$.data.status").value("PAID"));

		assertThat(walletPort.payCalls()).hasSize(1);
	}

	@Test
	@DisplayName("잔액이 부족하면 409로 부족액을 알려 주고 주문·자리는 그대로이며, 충전 뒤 같은 주문으로 결제된다")
	void payWithInsufficientBalance() throws Exception {
		long orderId = orderAndGetId(MEMBER_ID);
		walletPort.setBalance(TICKET_PRICE - 12_000);

		pay(MEMBER_ID, orderId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("예치금이 12,000원 부족합니다. 충전한 뒤 결제 기한 안에 다시 결제해주세요."))
			.andExpect(jsonPath("$.code").value("ORDER_INSUFFICIENT_BALANCE"))
			.andExpect(jsonPath("$.data.shortage").value(12_000));
		assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CREATED);
		assertThat(participationOf(orderId).getStatus()).isEqualTo(ParticipationStatus.APPLIED);

		walletPort.setBalance(TICKET_PRICE);
		pay(MEMBER_ID, orderId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("PAID"));
	}

	@Test
	@DisplayName("상품 서비스에서 기한이 지나 판매가 거절되면 예치금을 돌려주고 409이며, 주문은 PAID가 되지 않는다")
	void paySellExpired() throws Exception {
		long orderId = orderAndGetId(MEMBER_ID);
		seatHoldPort.expire(participationOf(orderId).getId());

		pay(MEMBER_ID, orderId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("결제 기한이 지나 결제하지 못했습니다. 빠져나간 예치금은 돌려드렸습니다."))
			.andExpect(jsonPath("$.code").value("ORDER_PAYMENT_DEADLINE_PASSED"));

		assertThat(walletPort.canceledOrders()).containsExactly(orderId);
		assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CREATED);
		assertThat(participationOf(orderId).getStatus()).isEqualTo(ParticipationStatus.APPLIED);
	}

	@Test
	@DisplayName("주문의 결제 기한이 지났으면 예치금을 건드리지 않고 409, 만료 처리된 주문도 같은 기한 지남 409다")
	void payAfterDue() throws Exception {
		long orderId = orderAndGetId(MEMBER_ID);
		Order order = orderRepository.findById(orderId).orElseThrow();
		ReflectionTestUtils.setField(order, "paymentDueAt", LocalDateTime.now().minusSeconds(1));
		orderRepository.save(order);

		pay(MEMBER_ID, orderId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("결제 기한이 지났습니다."))
			.andExpect(jsonPath("$.code").value("ORDER_PAYMENT_DEADLINE_PASSED"));

		orderExpiryUseCase.expireOverdue(LocalDateTime.now());
		pay(MEMBER_ID, orderId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("결제 기한이 지났습니다."))
			.andExpect(jsonPath("$.code").value("ORDER_PAYMENT_DEADLINE_PASSED"));
		assertThat(walletPort.payCalls()).isEmpty();
	}

	@Test
	@DisplayName("참가 신청이 결제 대기가 아니면 예치금을 차감하기 전에 409로 거절한다")
	void payWhenParticipationNotApplied() throws Exception {
		long orderId = orderAndGetId(MEMBER_ID);
		Participation participation = participationOf(orderId);
		ReflectionTestUtils.setField(participation, "status", ParticipationStatus.RELEASED);
		participationRepository.save(participation);

		pay(MEMBER_ID, orderId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("결제 대기 중인 신청만 참가를 확정할 수 있습니다(현재 상태: 미결제 해제)."));

		assertThat(walletPort.payCalls()).isEmpty();
		assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CREATED);
	}

	@Test
	@DisplayName("차감·판매 뒤 주문 저장이 롤백되면, 상품 서비스 기한이 지난 뒤 다시 결제해도 이미 판 자리라 결제되고 예치금은 한 번만 빠진다")
	void payAgainAfterRollback() throws Exception {
		long orderId = orderAndGetId(MEMBER_ID);
		long participationId = participationOf(orderId).getId();
		long balanceBefore = walletPort.balance();
		// 예치금 차감(D)과 자리 판매(B)는 끝났는데 이쪽 트랜잭션만 롤백된 상황
		transactionTemplate.executeWithoutResult(status -> {
			orderPayUseCase.pay(MEMBER_ID, orderId, LocalDateTime.now());
			status.setRollbackOnly();
		});
		assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CREATED);
		seatHoldPort.expire(participationId);

		pay(MEMBER_ID, orderId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("PAID"));

		assertThat(walletPort.canceledOrders()).isEmpty();
		assertThat(walletPort.balance()).isEqualTo(balanceBefore - TICKET_PRICE);
		assertThat(participationOf(orderId).getStatus()).isEqualTo(ParticipationStatus.CONFIRMED);
	}

	@Test
	@DisplayName("결제와 참가자 정보 수정이 동시에 들어와도 참가 확정과 고친 정보가 모두 남는다")
	void payWhileChangingParticipant() throws Exception {
		int rounds = 5;
		for (long memberId = 1; memberId <= rounds; memberId++) {
			long orderId = orderAndGetId(memberId);
			long participationId = participationOf(orderId).getId();
			long finalMemberId = memberId;

			CountDownLatch start = new CountDownLatch(1);
			List<Callable<Integer>> requests = List.of(
				() -> {
					start.await();
					return pay(finalMemberId, orderId).andReturn().getResponse().getStatus();
				},
				() -> {
					start.await();
					return changeParticipantName(finalMemberId, participationId, "박러너")
						.andReturn().getResponse().getStatus();
				});
			List<Integer> statuses = new ArrayList<>();
			try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
				List<Future<Integer>> futures = requests.stream().map(executor::submit).toList();
				start.countDown();
				for (Future<Integer> future : futures) {
					statuses.add(future.get());
				}
			}

			assertThat(statuses).containsExactly(200, 200);
			Participation participation = participationOf(orderId);
			assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.CONFIRMED);
			assertThat(participation.getParticipant().getName()).isEqualTo("박러너");
		}
	}

	@Test
	@DisplayName("다른 회원의 주문은 403, 없는 주문은 404이고 예치금을 건드리지 않는다")
	void payNotOwnerOrMissing() throws Exception {
		long orderId = orderAndGetId(MEMBER_ID);

		pay(OTHER_MEMBER_ID, orderId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("본인의 주문만 결제할 수 있습니다."))
			.andExpect(jsonPath("$.code").value("ORDER_FORBIDDEN"));
		pay(MEMBER_ID, 999999)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("존재하지 않는 주문입니다."))
			.andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
		assertThat(walletPort.payCalls()).isEmpty();
	}

	@Test
	@DisplayName("결제가 끝나면 만료 정리가 돌아도 주문·참가 신청이 그대로다")
	void paidOrderIsNotExpired() throws Exception {
		long orderId = orderAndGetId(MEMBER_ID);
		pay(MEMBER_ID, orderId).andExpect(status().isOk());

		orderExpiryUseCase.expireOverdue(LocalDateTime.now().plusHours(1));

		assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
		assertThat(participationOf(orderId).getStatus()).isEqualTo(ParticipationStatus.CONFIRMED);
	}

	private ResultActions pay(long memberId, long orderId) throws Exception {
		return mockMvc.perform(post(BASE_URL + "/" + orderId + "/payment").header("X-Member-Id", memberId));
	}

	private ResultActions changeParticipantName(long memberId, long participationId, String name) throws Exception {
		return mockMvc.perform(put("/api/v1/order/participations/" + participationId + "/participant")
			.header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{ "name": "%s", "birth": "1994-06-25", "gender": "MALE", "phone": "010-1234-5678",
					"emergencyContactName": "김가족", "emergencyContactPhone": "010-9876-5432", "tshirtSize": "L",
						"shippingAddress": { "recipientName": "김러너", "recipientPhone": "010-1234-5678",
							"zipcode": "04700", "address": "서울 성동구 왕십리로 00", "addressDetail": "101동 1001호" } }
				""".formatted(name)));
	}

	private Participation participationOf(long orderId) {
		Long participationId = transactionTemplate.execute(status ->
			orderRepository.findById(orderId).orElseThrow().participationId());
		return participationRepository.findById(participationId).orElseThrow();
	}

	// 신청서 작성 → [결제하기]로 결제 대기 주문을 만든다
	private long orderAndGetId(long memberId) throws Exception {
		QueueEntry.enter(mockMvc, memberId, COURSE_ID);
		long participationId = read(mockMvc.perform(post("/api/v1/order/participations")
			.header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{ "raceCourseId": %d, "thirdPartyConsent": true,
					"participant": { "name": "김러너", "birth": "1994-06-25", "gender": "MALE",
						"phone": "010-1234-5678", "emergencyContactName": "김가족",
						"emergencyContactPhone": "010-9876-5432", "tshirtSize": "L",
						"shippingAddress": { "recipientName": "김러너", "recipientPhone": "010-1234-5678",
							"zipcode": "04700", "address": "서울 성동구 왕십리로 00", "addressDetail": "101동 1001호" } } }
				""".formatted(COURSE_ID))));
		return read(mockMvc.perform(post(BASE_URL)
			.header("X-Member-Id", memberId)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"participationId\": %d}".formatted(participationId))));
	}

	private long read(ResultActions result) throws Exception {
		String body = result.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(body, "$.data.id").longValue();
	}
}
