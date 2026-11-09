package com.bukang.order.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import com.bukang.common.shared.notification.NotificationKafkaSender;
import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.order.domain.Participation;
import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.domain.Party;
import com.bukang.order.domain.PartyStatus;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.PartyMemberRepository;
import com.bukang.order.out.PartyRepository;
import com.bukang.order.out.seat.CrewSeatHoldPort;
import com.bukang.order.out.seat.CrewSeatHoldPort.CrewSeatHoldResult;
import com.bukang.order.out.seat.CrewSeatHoldPort.Shortage;
import com.bukang.order.support.StubCrewPort;
import com.bukang.order.support.StubCrewPortConfig;
import com.bukang.order.support.StubQueueAdmissionPort;
import com.bukang.order.support.StubQueueAdmissionPortConfig;
import com.bukang.order.support.StubRaceCoursePort;
import com.bukang.order.support.StubRaceCoursePortConfig;
import com.jayway.jsonpath.JsonPath;

// 파티장 [신청] (BUK-37). 크루 자리는 가짜 어댑터(종목마다 20석)를 감싸 부른 횟수를 본다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubCrewPortConfig.class, StubQueueAdmissionPortConfig.class})
class ApiV1PartyApplyTest {
	private static final String BASE_URL = "/api/v1/order/parties";
	private static final long LEADER = 1;
	private static final List<Long> MEMBERS = List.of(2L, 3L, 4L);
	private static final long OUTSIDER = 9;
	private static final long CREW_ID = 7;
	private static final long COURSE_ID = 301;
	private static final long RACE_ID = 300;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PartyRepository partyRepository;

	@Autowired
	private PartyMemberRepository partyMemberRepository;

	@Autowired
	private ParticipationRepository participationRepository;

	@Autowired
	private StubQueueAdmissionPort queueAdmissionPort;

	@Autowired
	private StubRaceCoursePort raceCoursePort;

	@Autowired
	private StubCrewPort crewPort;

	@MockitoSpyBean
	private CrewSeatHoldPort crewSeatHoldPort;

	@MockitoSpyBean
	private NotificationKafkaSender notificationSender;

	private LocalDateTime crewSaleStartAt;

	@BeforeEach
	void setUp() {
		crewSaleStartAt = LocalDateTime.now().minusDays(1);
		putCourse(crewSaleStartAt, LocalDateTime.now().plusDays(20), 4, 10);
	}

	@AfterEach
	void tearDown() {
		participationRepository.deleteAll();
		partyMemberRepository.deleteAll();
		partyRepository.deleteAll();
		queueAdmissionPort.clear();
		raceCoursePort.clear();
		crewPort.clear();
	}

	@Test
	@DisplayName("파티장이 신청하면 자리를 한꺼번에 잡고, 파티는 결제 진행 중, 신청서는 결제 대기, 파티원 모두에게 결제 알림이 간다")
	void apply() throws Exception {
		long partyId = partyWith(MEMBERS);
		// DB는 마이크로초까지 남기므로 맞춰 비교한다 (Linux CI는 나노초)
		LocalDateTime before = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

		apply(LEADER, partyId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("PAYING"))
			.andExpect(jsonPath("$.data.paymentDueAt").isNotEmpty());

		Party party = partyRepository.findById(partyId).orElseThrow();
		assertThat(party.getStatus()).isEqualTo(PartyStatus.PAYING);
		assertThat(party.getAppliedAt()).isNotNull();
		// 크루 마감(20일 뒤)·접수 마감(30일 뒤)보다 신청 뒤 24시간이 이르다
		assertThat(party.getPaymentDueAt())
			.isBetween(before.plusHours(24), LocalDateTime.now().plusHours(24));
		assertThat(participationRepository.findByPartyIdIn(List.of(partyId)))
			.hasSize(4)
			.allSatisfy(participation -> {
				assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.APPLIED);
				assertThat(participation.getPaymentDueAt().truncatedTo(ChronoUnit.MILLIS))
					.isEqualTo(party.getPaymentDueAt().truncatedTo(ChronoUnit.MILLIS));
			});
		ArgumentCaptor<NotificationRequestedEvent> sent = ArgumentCaptor.forClass(NotificationRequestedEvent.class);
		verify(notificationSender, times(4)).send(sent.capture());
		assertThat(sent.getAllValues())
			.extracting(NotificationRequestedEvent::memberId, NotificationRequestedEvent::kind)
			.containsExactlyInAnyOrder(tuple(1L, NotificationKind.PARTY_PAYMENT_REQUESTED),
				tuple(2L, NotificationKind.PARTY_PAYMENT_REQUESTED),
				tuple(3L, NotificationKind.PARTY_PAYMENT_REQUESTED),
				tuple(4L, NotificationKind.PARTY_PAYMENT_REQUESTED));

		// 같은 파티는 다시 신청할 수 없다. 상품도 다시 부르지 않는다
		apply(LEADER, partyId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_NOT_RECRUITING"))
			.andExpect(jsonPath("$.data.partyStatus").value("PAYING"));
		// 크루 마감이 지난 뒤 다시 눌러도 「이미 신청함」이다 (기간 확인보다 먼저 거른다)
		putCourse(crewSaleStartAt, LocalDateTime.now().minusMinutes(1), 4, 10);
		apply(LEADER, partyId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_NOT_RECRUITING"));
		verify(crewSeatHoldPort, times(1)).holdAll(eq(partyId), eq(CREW_ID), eq(RACE_ID), any(), any());
		// 결제 진행 중에는 합류·나가기도 막힌다
		post(MEMBERS.getFirst(), partyId, "/leave").andExpect(status().isConflict());
	}

	@Test
	@DisplayName("크루 자리가 모자라면 한 석도 잡지 않고 409와 모자란 종목을 주며, 파티는 모집 중으로 남는다")
	void shortage() throws Exception {
		long partyId = partyWith(MEMBERS);
		doReturn(CrewSeatHoldResult.shortOf(Map.of(COURSE_ID, new Shortage(4, 1))))
			.when(crewSeatHoldPort).holdAll(anyLong(), anyLong(), anyLong(), any(), any());

		apply(LEADER, partyId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_CREW_SEAT_SHORTAGE"))
			.andExpect(jsonPath("$.data.partyId").value(partyId))
			.andExpect(jsonPath("$.data.raceCourseId").value(COURSE_ID))
			.andExpect(jsonPath("$.data.needed").value(4))
			.andExpect(jsonPath("$.data.remaining").value(1));

		assertThat(partyRepository.findById(partyId).orElseThrow().getStatus()).isEqualTo(PartyStatus.RECRUITING);
		assertThat(participationRepository.findByPartyIdIn(List.of(partyId)))
			.extracting(Participation::getStatus)
			.containsOnly(ParticipationStatus.PENDING);
		verify(notificationSender, never()).send(any());
	}

	@Test
	@DisplayName("상품이 처음 잡은 기한을 돌려주면(응답을 못 받고 다시 신청) 그 기한을 파티·신청서 결제 기한으로 쓴다")
	void usesHeldExpiresAt() throws Exception {
		long partyId = partyWith(MEMBERS);
		LocalDateTime firstDueAt = LocalDateTime.now().plusHours(23).truncatedTo(ChronoUnit.MICROS);
		doReturn(CrewSeatHoldResult.held(firstDueAt))
			.when(crewSeatHoldPort).holdAll(anyLong(), anyLong(), anyLong(), any(), any());

		apply(LEADER, partyId).andExpect(status().isOk());

		assertThat(partyRepository.findById(partyId).orElseThrow().getPaymentDueAt()).isEqualTo(firstDueAt);
		assertThat(participationRepository.findByPartyIdIn(List.of(partyId)))
			.extracting(Participation::getPaymentDueAt)
			.containsOnly(firstDueAt);
	}

	@Test
	@DisplayName("상품이 돌려준 처음 기한이 이미 지났으면 결제 기간을 열지 않고 409, 파티는 모집 중으로 남는다")
	void heldExpiresAtPassed() throws Exception {
		long partyId = partyWith(MEMBERS);
		doReturn(CrewSeatHoldResult.held(LocalDateTime.now().minusMinutes(1)))
			.when(crewSeatHoldPort).holdAll(anyLong(), anyLong(), anyLong(), any(), any());

		apply(LEADER, partyId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PARTICIPATION_NOT_PAYABLE"));
		assertThat(partyRepository.findById(partyId).orElseThrow().getStatus()).isEqualTo(PartyStatus.RECRUITING);
		verify(notificationSender, never()).send(any());
	}

	@Test
	@DisplayName("파티장이 아니거나 파티 대기열에 입장하지 않았으면 거절하고 상품을 부르지 않는다")
	void leaderAndQueue() throws Exception {
		long partyId = partyWith(MEMBERS);
		// 개인 대기열에는 입장 중이어도 파티 대기열에 입장하지 않았으면 막힌다
		queueAdmissionPort.notAdmittedToParty(COURSE_ID);

		apply(MEMBERS.getFirst(), partyId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_LEADER_ONLY"));
		apply(LEADER, partyId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORDER_QUEUE_NOT_ADMITTED"));
		verify(crewSeatHoldPort, never()).holdAll(anyLong(), anyLong(), anyLong(), any(), any());
	}

	@Test
	@DisplayName("파티원이 4명보다 적거나 판매자 최소·최대 인원 밖이면 거절하고 상품을 부르지 않는다")
	void size() throws Exception {
		long partyId = partyWith(MEMBERS.subList(0, 2));

		// 판매자 최소 인원이 2명이어도 4명은 있어야 한다 (11/05 회의 P3)
		putCourse(crewSaleStartAt, LocalDateTime.now().plusDays(20), 2, 10);
		apply(LEADER, partyId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_SIZE_OUT_OF_RANGE"));

		putCourse(crewSaleStartAt, LocalDateTime.now().plusDays(20), 4, 2);
		apply(LEADER, partyId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_SIZE_OUT_OF_RANGE"));
		verify(crewSeatHoldPort, never()).holdAll(anyLong(), anyLong(), anyLong(), any(), any());
	}

	@Test
	@DisplayName("크루를 떠난 파티원이나 파티 예매 시작 7일 전보다 늦게 가입한 파티원이 있으면 거절하고 상품을 부르지 않는다")
	void crewMembers() throws Exception {
		long partyId = partyWith(MEMBERS);

		crewPort.rejoinedCrewAt(MEMBERS.getLast(), crewSaleStartAt.minusDays(7).plusMinutes(1));
		apply(LEADER, partyId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_MEMBER_JOINED_LATE"));

		// 딱 7일 전에 가입했으면 된다. 대신 크루를 떠난 사람이 있다
		crewPort.rejoinedCrewAt(MEMBERS.getLast(), crewSaleStartAt.minusDays(7));
		crewPort.notCrewMember(MEMBERS.getFirst());
		apply(LEADER, partyId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_MEMBER_NOT_CREW"));
		verify(crewSeatHoldPort, never()).holdAll(anyLong(), anyLong(), anyLong(), any(), any());

		crewPort.clear();
		crewPort.rejoinedCrewAt(MEMBERS.getLast(), crewSaleStartAt.minusDays(7));
		apply(LEADER, partyId).andExpect(status().isOk());
	}

	@Test
	@DisplayName("파티 예매 기간(크루 예매 시작~마감) 밖이면 거절하고 상품을 부르지 않는다")
	void salePeriod() throws Exception {
		long partyId = partyWith(MEMBERS);

		putCourse(LocalDateTime.now().plusHours(1), LocalDateTime.now().plusDays(20), 4, 10);
		apply(LEADER, partyId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_SALE_CLOSED"));

		putCourse(crewSaleStartAt, LocalDateTime.now().minusMinutes(1), 4, 10);
		apply(LEADER, partyId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_SALE_CLOSED"));
		verify(crewSeatHoldPort, never()).holdAll(anyLong(), anyLong(), anyLong(), any(), any());
	}

	@Test
	@DisplayName("크루원을 확인한 뒤 잠그기 전에 누가 합류하면, 확인하지 않은 사람이 섞이므로 409로 다시 누르게 한다")
	void membersChanged() throws Exception {
		long partyId = partyWith(MEMBERS);
		String token = partyRepository.findById(partyId).orElseThrow().getJoinToken();
		crewPort.beforeNextLookup(() -> {
			// 다른 요청(다른 트랜잭션)으로 합류를 끝낸다
			try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
				executor.submit(() -> join(OUTSIDER, token).andExpect(status().isCreated())).get();
			} catch (Exception exception) {
				throw new IllegalStateException(exception);
			}
		});

		apply(LEADER, partyId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_MEMBERS_CHANGED"));
		verify(crewSeatHoldPort, never()).holdAll(anyLong(), anyLong(), anyLong(), any(), any());

		// 다시 누르면 새 파티원까지 확인해 신청된다
		apply(LEADER, partyId).andExpect(status().isOk());
	}

	@Test
	@DisplayName("취소된 대회는 신청할 수 없고 상품 자리를 부르지 않는다")
	void canceledRace() throws Exception {
		long partyId = partyWith(MEMBERS);
		raceCoursePort.put(StubRaceCoursePort.canceled(StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null)));

		apply(LEADER, partyId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_RACE_CANCELED"));
		verify(crewSeatHoldPort, never()).holdAll(anyLong(), anyLong(), anyLong(), any(), any());
	}

	@Test
	@DisplayName("판매자 최대 인원이 찼으면 합류할 수 없다")
	void joinFull() throws Exception {
		putCourse(crewSaleStartAt, LocalDateTime.now().plusDays(20), 4, 4);
		long partyId = partyWith(MEMBERS);
		String token = partyRepository.findById(partyId).orElseThrow().getJoinToken();

		join(OUTSIDER, token)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_FULL"));

		// 한 명이 나가면 자리가 난다
		post(MEMBERS.getFirst(), partyId, "/leave").andExpect(status().isOk());
		join(OUTSIDER, token).andExpect(status().isCreated());
	}

	private void putCourse(LocalDateTime startAt, LocalDateTime endAt, int minCount, int maxCount) {
		raceCoursePort.put(StubRaceCoursePort.withCrewSale(StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null),
			startAt, endAt, minCount, maxCount));
	}

	// 파티장 + 파티원으로 파티를 만든다
	private long partyWith(List<Long> members) throws Exception {
		String created = mockMvc.perform(MockMvcRequestBuilders.post(BASE_URL)
				.header("X-Member-Id", LEADER)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "crewId": %d, "raceCourseId": %d, "participant": %s, "thirdPartyConsent": true }
					""".formatted(CREW_ID, COURSE_ID, participant())))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		String token = JsonPath.read(created, "$.data.party.joinToken");
		for (long member : members) {
			join(member, token).andExpect(status().isCreated());
		}
		return ((Number)JsonPath.read(created, "$.data.party.id")).longValue();
	}

	private ResultActions join(long memberId, String token) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.post(BASE_URL + "/join")
			.header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{ "joinToken": "%s", "participant": %s, "thirdPartyConsent": true }
				""".formatted(token, participant())));
	}

	private ResultActions apply(long memberId, long partyId) throws Exception {
		return post(memberId, partyId, "/apply");
	}

	private ResultActions post(long memberId, long partyId, String action) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.post(BASE_URL + "/" + partyId + action)
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
