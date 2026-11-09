package com.bukang.order.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.shared.notification.NotificationKafkaSender;
import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.order.app.OrderExpiryUseCase;
import com.bukang.order.app.OrderPayUseCase;
import com.bukang.order.app.PartyPaymentDeadlineUseCase;
import com.bukang.order.domain.Participation;
import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.domain.Party;
import com.bukang.order.domain.PartyStatus;
import com.bukang.order.domain.exception.OrderNotPayableException;
import com.bukang.order.out.OrderOutboxKafkaEventRepository;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.PartyMemberRepository;
import com.bukang.order.out.PartyRepository;
import com.bukang.order.out.QueueAdmissionRepository;
import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.support.QueueEntry;
import com.bukang.order.support.SeatOutboxMessages;
import com.bukang.order.support.StubCrewPort;
import com.bukang.order.support.StubCrewPortConfig;
import com.bukang.order.support.StubRaceCoursePort;
import com.bukang.order.support.StubRaceCoursePortConfig;
import com.bukang.order.support.StubWalletPort;
import com.bukang.order.support.StubWalletPortConfig;
import com.jayway.jsonpath.JsonPath;

// 파티원 결제와 결제 기간 마감 (BUK-38). 크루 자리는 가짜 어댑터(종목마다 20석)라 테스트마다 다른 종목을 쓴다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubCrewPortConfig.class, StubWalletPortConfig.class})
class ApiV1PartyPaymentTest {
	private static final String PARTY_URL = "/api/v1/order/parties";
	private static final String ORDER_URL = "/api/v1/order/orders";
	private static final long LEADER = 1;
	private static final List<Long> MEMBERS = List.of(2L, 3L, 4L);
	private static final List<Long> EVERYONE = List.of(1L, 2L, 3L, 4L);
	private static final long CREW_ID = 7;
	private static final long RACE_ID = 400;
	private static final AtomicLong NEXT_COURSE_ID = new AtomicLong(4000);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PartyRepository partyRepository;

	@Autowired
	private PartyMemberRepository partyMemberRepository;

	@Autowired
	private ParticipationRepository participationRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private QueueAdmissionRepository queueAdmissionRepository;

	@Autowired
	private OrderOutboxKafkaEventRepository outboxRepository;

	@Autowired
	private StubRaceCoursePort raceCoursePort;

	@Autowired
	private StubCrewPort crewPort;

	@Autowired
	private StubWalletPort walletPort;

	@Autowired
	private PartyPaymentDeadlineUseCase deadlineUseCase;

	@Autowired
	private OrderPayUseCase orderPayUseCase;

	@Autowired
	private OrderExpiryUseCase orderExpiryUseCase;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@MockitoSpyBean
	private NotificationKafkaSender notificationSender;

	private long courseId;

	@BeforeEach
	void setUp() {
		courseId = NEXT_COURSE_ID.incrementAndGet();
		raceCoursePort.put(course());
	}

	@AfterEach
	void tearDown() {
		orderRepository.deleteAll();
		outboxRepository.deleteAll();
		participationRepository.deleteAll();
		partyMemberRepository.deleteAll();
		partyRepository.deleteAll();
		queueAdmissionRepository.deleteAll();
		raceCoursePort.clear();
		crewPort.clear();
		walletPort.clear();
	}

	@Test
	@DisplayName("파티원은 주문을 만들어 각자 결제하고, 기한이 지나면 결제하지 않은 신청서가 끝나 자리 해제를 보내며 파티는 결제 끝이 된다")
	void payThenDeadline() throws Exception {
		long partyId = appliedParty();
		Map<Long, Long> participationIds = participationIdsByMember(partyId);

		long leaderOrder = createOrder(LEADER, participationIds.get(LEADER));
		pay(LEADER, leaderOrder).andExpect(status().isOk());
		pay(2, createOrder(2, participationIds.get(2L))).andExpect(status().isOk());
		assertThat(statusOf(participationIds.get(LEADER))).isEqualTo(ParticipationStatus.CONFIRMED);
		assertThat(walletPort.payCalls()).hasSize(2);

		// 기한 전이고 결제 대기가 남았으면 그대로 둔다
		assertThat(deadlineUseCase.finishDue(LocalDateTime.now())).isZero();
		assertThat(partyOf(partyId).getStatus()).isEqualTo(PartyStatus.PAYING);

		clearInvocations(notificationSender);
		LocalDateTime afterDue = partyOf(partyId).getPaymentDueAt().plusSeconds(1);
		assertThat(deadlineUseCase.finishDue(afterDue)).isEqualTo(1);

		assertThat(partyOf(partyId).getStatus()).isEqualTo(PartyStatus.PAID);
		assertThat(statusOf(participationIds.get(3L))).isEqualTo(ParticipationStatus.RELEASED);
		assertThat(statusOf(participationIds.get(4L))).isEqualTo(ParticipationStatus.RELEASED);
		assertThat(SeatOutboxMessages.released(outboxRepository))
			.containsExactlyInAnyOrder(participationIds.get(3L), participationIds.get(4L));
		// 확정·미결제 수는 신청서에서 센 값이다
		List<NotificationRequestedEvent> sent = sentNotifications();
		assertThat(sent).hasSize(4)
			.allSatisfy(event -> {
				assertThat(event.kind()).isEqualTo(NotificationKind.PARTY_PAYMENT_DEADLINE);
				assertThat(event.body()).contains("결제 2명, 결제하지 않음 2명");
			});
		// 다시 돌려도 바뀌지 않는다
		assertThat(deadlineUseCase.finishDue(afterDue.plusMinutes(1))).isZero();

		// 결제하지 않아 끝난 파티원은 같은 대회에 개인으로 다시 신청할 수 있다 (11/03 결정)
		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/order/participations")
				.header("X-Member-Id", 3L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "raceCourseId": %d, "participant": %s, "thirdPartyConsent": true }
					""".formatted(courseId, participant())))
			.andExpect(status().isCreated());
		// 결제한 파티원은 결제 끝 파티에 남아 있어 개인 신청을 막는다. 화면이 나누도록 파티 상태를 준다 (BUK-127)
		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/order/participations")
				.header("X-Member-Id", 2L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "raceCourseId": %d, "participant": %s, "thirdPartyConsent": true }
					""".formatted(courseId, participant())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_ALREADY_JOINED"))
			.andExpect(jsonPath("$.data.partyId").value(partyId))
			.andExpect(jsonPath("$.data.partyStatus").value("PAID"));
	}

	@Test
	@DisplayName("모두 결제하면 기한 전이라도 파티가 결제 끝이 된다")
	void allPaid() throws Exception {
		long partyId = appliedParty();
		Map<Long, Long> participationIds = participationIdsByMember(partyId);
		for (long member : EVERYONE) {
			pay(member, createOrder(member, participationIds.get(member))).andExpect(status().isOk());
		}

		assertThat(deadlineUseCase.finishDue(LocalDateTime.now())).isEqualTo(1);

		assertThat(partyOf(partyId).getStatus()).isEqualTo(PartyStatus.PAID);
		assertThat(SeatOutboxMessages.released(outboxRepository)).isEmpty();
	}

	@RepeatedTest(3)
	@DisplayName("파티원이 동시에 결제해도 결제 인원이 빠지지 않는다")
	void concurrentPay() throws Exception {
		long partyId = appliedParty();
		Map<Long, Long> participationIds = participationIdsByMember(partyId);
		Map<Long, Long> orderIds = new HashMap<>();
		for (long member : EVERYONE) {
			orderIds.put(member, createOrder(member, participationIds.get(member)));
		}

		ExecutorService executor = Executors.newFixedThreadPool(EVERYONE.size());
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for (long member : EVERYONE) {
				results.add(executor.submit(() ->
					pay(member, orderIds.get(member)).andReturn().getResponse().getStatus()));
			}
			for (Future<Integer> result : results) {
				assertThat(result.get()).isEqualTo(200);
			}
		} finally {
			executor.shutdown();
		}

		deadlineUseCase.finishDue(LocalDateTime.now());
		assertThat(partyOf(partyId).getStatus()).isEqualTo(PartyStatus.PAID);
		assertThat(participationRepository.findByPartyIdIn(List.of(partyId)))
			.extracting(Participation::getStatus)
			.containsOnly(ParticipationStatus.CONFIRMED);
	}

	@Test
	@DisplayName("기한이 지난 뒤 결제하면 실패하고 예치금이 빠지지 않으며, 아무도 결제하지 않은 파티는 주문 만료 뒤 종료된다")
	void payAfterDeadline() throws Exception {
		long partyId = appliedParty();
		long participationId = participationIdsByMember(partyId).get(2L);
		long orderId = createOrder(2, participationId);
		LocalDateTime afterDue = partyOf(partyId).getPaymentDueAt().plusSeconds(1);

		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
			orderPayUseCase.pay(2, orderId, afterDue)))
			.isInstanceOf(OrderNotPayableException.class);
		assertThat(walletPort.payCalls()).isEmpty();

		// 주문을 만든 신청서는 주문 만료가 끝낼 때까지 파티 마감이 기다린다
		assertThat(deadlineUseCase.finishDue(afterDue)).isZero();
		assertThat(statusOf(participationId)).isEqualTo(ParticipationStatus.APPLIED);
		orderExpiryUseCase.expireOverdue(afterDue);
		assertThat(deadlineUseCase.finishDue(afterDue)).isEqualTo(1);

		assertThat(partyOf(partyId).getStatus()).isEqualTo(PartyStatus.CLOSED);
		assertThat(participationRepository.findByPartyIdIn(List.of(partyId)))
			.extracting(Participation::getStatus)
			.containsOnly(ParticipationStatus.RELEASED);
		// 4명 모두 자리 해제를 보낸다 (주문 만료 1건 + 마감 3건)
		assertThat(SeatOutboxMessages.released(outboxRepository)).hasSize(4);
	}

	@Test
	@DisplayName("크루를 떠난 파티원이나 대회가 취소된 뒤에는 예치금을 빼기 전에 결제를 막는다")
	void crewLeftOrRaceCanceled() throws Exception {
		long partyId = appliedParty();
		Map<Long, Long> participationIds = participationIdsByMember(partyId);
		long orderId = createOrder(2, participationIds.get(2L));
		long otherOrderId = createOrder(3, participationIds.get(3L));

		crewPort.notCrewMember(2);
		pay(2, orderId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_MEMBER_NOT_CREW"));

		raceCoursePort.put(StubRaceCoursePort.canceled(course()));
		pay(3, otherOrderId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_RACE_CANCELED"));
		assertThat(walletPort.payCalls()).isEmpty();
	}

	@Test
	@DisplayName("파티장이 신청하기 전에는 파티원 신청서로 주문을 만들 수 없다")
	void beforeApply() throws Exception {
		long partyId = party();
		long participationId = participationIdsByMember(partyId).get(2L);

		order(2, participationId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PARTICIPATION_NOT_PAYABLE"));
	}

	@Test
	@DisplayName("기한 3시간 전에 결제하지 않은 파티원에게만 마감 알림을 한 번 보낸다")
	void remind() throws Exception {
		long partyId = appliedParty();
		Map<Long, Long> participationIds = participationIdsByMember(partyId);
		pay(LEADER, createOrder(LEADER, participationIds.get(LEADER))).andExpect(status().isOk());
		LocalDateTime dueAt = partyOf(partyId).getPaymentDueAt();
		clearInvocations(notificationSender);

		assertThat(deadlineUseCase.remindDue(dueAt.minusHours(4))).isZero();
		assertThat(deadlineUseCase.remindDue(dueAt.minusHours(2))).isEqualTo(1);
		assertThat(deadlineUseCase.remindDue(dueAt.minusHours(1))).isZero();

		assertThat(sentNotifications())
			.extracting(NotificationRequestedEvent::memberId)
			.containsExactlyInAnyOrderElementsOf(MEMBERS);
		assertThat(sentNotifications()).allSatisfy(event ->
			assertThat(event.kind()).isEqualTo(NotificationKind.PARTY_PAYMENT_DEADLINE));
	}

	@Test
	@DisplayName("신청 때 기한이 3시간 안이면 마감 알림을 따로 보내지 않는다")
	void noRemindForShortWindow() throws Exception {
		raceCoursePort.put(StubRaceCoursePort.withCrewSale(StubRaceCoursePort.openCourse(courseId, RACE_ID, 14, null),
			LocalDateTime.now().minusDays(1), LocalDateTime.now().plusHours(2), 4, 10));
		long partyId = appliedParty();
		clearInvocations(notificationSender);

		assertThat(deadlineUseCase.remindDue(LocalDateTime.now().plusMinutes(30))).isZero();
		verify(notificationSender, never()).send(any());
	}

	private Party partyOf(long partyId) {
		return partyRepository.findById(partyId).orElseThrow();
	}

	private ParticipationStatus statusOf(long participationId) {
		return participationRepository.findById(participationId).orElseThrow().getStatus();
	}

	private Map<Long, Long> participationIdsByMember(long partyId) {
		return participationRepository.findByPartyIdIn(List.of(partyId)).stream()
			.collect(Collectors.toMap(Participation::getMemberId, Participation::getId));
	}

	private List<NotificationRequestedEvent> sentNotifications() {
		ArgumentCaptor<NotificationRequestedEvent> sent = ArgumentCaptor.forClass(NotificationRequestedEvent.class);
		verify(notificationSender, atLeast(0)).send(sent.capture());
		return sent.getAllValues();
	}

	private RaceCourseInfo course() {
		return StubRaceCoursePort.withCrewSale(StubRaceCoursePort.openCourse(courseId, RACE_ID, 14, null),
			LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(20), 4, 10);
	}

	// 파티장 + 파티원 3명 파티를 만들고 파티장이 신청한다
	private long appliedParty() throws Exception {
		long partyId = party();
		QueueEntry.enter(mockMvc, LEADER, courseId);
		mockMvc.perform(MockMvcRequestBuilders.post(PARTY_URL + "/" + partyId + "/apply").header("X-Member-Id", LEADER))
			.andExpect(status().isOk());
		return partyId;
	}

	private long party() throws Exception {
		String created = mockMvc.perform(MockMvcRequestBuilders.post(PARTY_URL)
				.header("X-Member-Id", LEADER)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "crewId": %d, "raceCourseId": %d, "participant": %s, "thirdPartyConsent": true }
					""".formatted(CREW_ID, courseId, participant())))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		long partyId = JsonPath.<Number>read(created, "$.data.party.id").longValue();
		String token = JsonPath.read(created, "$.data.party.joinToken");
		for (long member : MEMBERS) {
			mockMvc.perform(MockMvcRequestBuilders.post(PARTY_URL + "/join")
					.header("X-Member-Id", member)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
						{ "joinToken": "%s", "participant": %s, "thirdPartyConsent": true }
						""".formatted(token, participant())))
				.andExpect(status().isCreated());
		}
		return partyId;
	}

	private ResultActions order(long memberId, long participationId) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.post(ORDER_URL)
			.header("X-Member-Id", memberId)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"participationId\": %d}".formatted(participationId)));
	}

	private long createOrder(long memberId, long participationId) throws Exception {
		String body = order(memberId, participationId)
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(body, "$.data.id").longValue();
	}

	private ResultActions pay(long memberId, long orderId) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.post(ORDER_URL + "/" + orderId + "/payment")
			.header("X-Member-Id", memberId));
	}

	private static String participant() {
		return """
			{ "name": "김러너", "birth": "1994-06-25", "gender": "MALE", "phone": "010-1234-5678",
				"emergencyContactName": "김가족", "emergencyContactPhone": "010-9876-5432", "tshirtSize": "L",
				"shippingAddress": { "recipientName": "김러너", "recipientPhone": "010-1234-5678", "zipcode": "04700",
					"address": "서울 성동구 왕십리로 00", "addressDetail": "101동 1001호" } }
			""";
	}
}
