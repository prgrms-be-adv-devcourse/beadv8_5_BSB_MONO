package com.bukang.order.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.domain.Party;
import com.bukang.order.domain.PartyStatus;
import com.bukang.order.out.OrderOutboxKafkaEventRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.PartyMemberRepository;
import com.bukang.order.out.PartyRepository;
import com.bukang.order.support.StubCrewPort;
import com.bukang.order.support.StubCrewPortConfig;
import com.bukang.order.support.StubRaceCoursePort;
import com.bukang.order.support.StubRaceCoursePortConfig;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubCrewPortConfig.class})
class ApiV1PartyTest {
	private static final String BASE_URL = "/api/v1/order/parties";
	private static final String RACE_STATUS_URL = BASE_URL + "/me/race-status";
	private static final long LEADER = 1;
	private static final long MEMBER = 2;
	private static final long OTHER = 3;
	private static final long FOURTH = 4;
	private static final long CREW_ID = 7;
	private static final long COURSE_10K = 10;
	private static final long COURSE_HALF = 11;
	private static final long RACE_ID = 100;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PartyRepository partyRepository;

	@Autowired
	private PartyMemberRepository partyMemberRepository;

	@Autowired
	private ParticipationRepository participationRepository;

	@Autowired
	private OrderOutboxKafkaEventRepository orderOutboxKafkaEventRepository;

	@Autowired
	private StubRaceCoursePort raceCoursePort;

	@Autowired
	private StubCrewPort crewPort;

	@BeforeEach
	void setUp() {
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_10K, RACE_ID, 14, null));
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_HALF, RACE_ID, 14, null));
	}

	@AfterEach
	void tearDown() {
		participationRepository.deleteAll();
		partyMemberRepository.deleteAll();
		partyRepository.deleteAll();
		orderOutboxKafkaEventRepository.deleteAll();
		raceCoursePort.clear();
		crewPort.clear();
	}

	@Test
	@DisplayName("크루원이 파티를 만들면 모집 중 파티, 24시간 참가 링크, 파티장의 결제 전 파티원 신청서가 생긴다")
	void create() throws Exception {
		create(LEADER, COURSE_10K)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.party.status").value("RECRUITING"))
			.andExpect(jsonPath("$.data.party.raceId").value(RACE_ID))
			.andExpect(jsonPath("$.data.party.raceCourseId").value(COURSE_10K))
			.andExpect(jsonPath("$.data.party.leaderMemberId").value(LEADER))
			.andExpect(jsonPath("$.data.party.joinToken").isNotEmpty())
			.andExpect(jsonPath("$.data.participation.participationType").value("PARTY"))
			.andExpect(jsonPath("$.data.participation.status").value("PENDING"))
			.andExpect(jsonPath("$.data.participation.raceCourseId").value(COURSE_10K));

		Party party = partyRepository.findAll().getFirst();
		assertThat(party.getJoinTokenExpiresAt()).isAfter(LocalDateTime.now().plusHours(23));
		assertThat(participationRepository.findAll().getFirst().getPartyId()).isEqualTo(party.getId());
		assertThat(partyMemberRepository.findByPartyIdAndMemberId(party.getId(), LEADER)).isPresent();
	}

	@Test
	@DisplayName("크루원이 아니면 파티를 만들 수도 합류할 수도 없다 (403)")
	void crewMemberRequired() throws Exception {
		crewPort.notCrewMember(OTHER);
		create(OTHER, COURSE_10K)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORDER_CREW_MEMBER_REQUIRED"));
		assertThat(partyRepository.count()).isZero();

		String token = token(create(LEADER, COURSE_10K));
		join(OTHER, token)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORDER_CREW_MEMBER_REQUIRED"));
	}

	@Test
	@DisplayName("파티원은 종목을 고르지 않고 파티 종목으로 합류한다. 이미 합류했으면 409")
	void join() throws Exception {
		String token = token(create(LEADER, COURSE_HALF));
		long partyId = partyRepository.findAll().getFirst().getId();

		join(MEMBER, token)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.participationType").value("PARTY"))
			.andExpect(jsonPath("$.data.partyId").value(partyId))
			.andExpect(jsonPath("$.data.raceCourseId").value(COURSE_HALF));
		join(MEMBER, token)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_ALREADY_JOINED"))
			.andExpect(jsonPath("$.data.partyId").value(partyId));
	}

	@Test
	@DisplayName("합류할 때 나이 확인은 파티 종목 기준이다")
	void joinChecksPartyCourse() throws Exception {
		String token = token(create(LEADER, COURSE_HALF));
		// 만든 뒤 파티 종목의 나이 제한이 올라간 경우. 다른 종목(10K)은 그대로 열려 있다
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_HALF, RACE_ID, 60, null));

		join(MEMBER, token)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ORDER_AGE_RESTRICTED"));
	}

	@Test
	@DisplayName("같은 대회에 개인 신청이 있으면 합류를 거절하고 그 신청서 ID를 준다. 개인 신청을 취소하면 합류된다")
	void individualParticipationExists() throws Exception {
		String token = token(create(LEADER, COURSE_10K));
		long individualId = ((Number)JsonPath.read(mockMvc.perform(post("/api/v1/order/participations")
				.header("X-Member-Id", MEMBER)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(COURSE_10K)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();

		join(MEMBER, token)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_INDIVIDUAL_PARTICIPATION_EXISTS"))
			.andExpect(jsonPath("$.data.participationId").value(individualId));

		mockMvc.perform(post("/api/v1/order/participations/" + individualId + "/cancel").header("X-Member-Id", MEMBER))
			.andExpect(status().isOk());
		join(MEMBER, token).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("파티에 있는 사람은 같은 대회에 개인 신청도, 다른 파티 합류도 할 수 없다")
	void onePartyPerRace() throws Exception {
		String first = token(create(LEADER, COURSE_10K));
		String second = token(create(OTHER, COURSE_10K));
		long firstPartyId = partyRepository.findAll().stream()
			.filter(party -> party.getLeaderMemberId() == LEADER).findFirst().orElseThrow().getId();
		join(MEMBER, first).andExpect(status().isCreated());

		// 개인 신청 중복(ORDER_DUPLICATE_PARTICIPATION)과 다른 코드로, 이미 신청한 파티 번호를 준다
		join(MEMBER, second)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_OTHER_PARTY_JOINED"))
			.andExpect(jsonPath("$.data.partyId").value(firstPartyId));
		mockMvc.perform(post("/api/v1/order/participations")
				.header("X-Member-Id", MEMBER)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(COURSE_10K)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("파티로 신청한 대회예요. 개인으로 신청하려면 파티에서 나가 주세요."));
	}

	@Test
	@DisplayName("파티장이 링크를 다시 만들면 옛 링크는 404, 24시간 지난 링크는 409, 파티장이 아니면 다시 만들 수 없다")
	void joinLink() throws Exception {
		String oldToken = token(create(LEADER, COURSE_10K));
		long partyId = partyRepository.findAll().getFirst().getId();

		regenerate(MEMBER, partyId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_LEADER_ONLY"));
		String newToken = JsonPath.read(regenerate(LEADER, partyId)
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(), "$.data.joinToken");

		assertThat(newToken).isNotEqualTo(oldToken);
		join(MEMBER, oldToken)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_NOT_FOUND"));

		jdbcTemplate.update("update order_party set join_token_issued_at = ? where id = ?",
			LocalDateTime.now().minusHours(24).minusMinutes(1), partyId);
		join(MEMBER, newToken)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_LINK_EXPIRED"))
			.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	@DisplayName("나간 사람은 신청서가 취소되고, 같은 링크로 다시 합류하면 파티원 줄 하나가 되살아난다")
	void leaveAndRejoin() throws Exception {
		String token = token(create(LEADER, COURSE_10K));
		long partyId = partyRepository.findAll().getFirst().getId();
		join(MEMBER, token).andExpect(status().isCreated());

		leave(MEMBER, partyId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("CANCELED"));
		leave(MEMBER, partyId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_NOT_MEMBER"));
		// 그 사이 크루를 나갔다 다시 가입했으면, 되살린 줄의 크루 가입 시각도 새 값이 된다 (크루 탈퇴 이벤트와 비교할 값)
		LocalDateTime rejoinedCrewAt = StubCrewPort.JOINED_AT.plusDays(30);
		crewPort.rejoinedCrewAt(MEMBER, rejoinedCrewAt);
		join(MEMBER, token).andExpect(status().isCreated());

		assertThat(partyMemberRepository.findAll()).filteredOn(row -> row.getMemberId() == MEMBER)
			.singleElement().satisfies(row -> {
				assertThat(row.isActive()).isTrue();
				assertThat(row.getCrewJoinedAt()).isEqualTo(rejoinedCrewAt);
			});
		assertThat(partyMemberRepository.findAll()).filteredOn(row -> row.getMemberId() == LEADER)
			.singleElement().satisfies(row -> assertThat(row.getCrewJoinedAt()).isEqualTo(StubCrewPort.JOINED_AT));
		assertThat(participationRepository.findAll()).filteredOn(p -> p.getMemberId() == MEMBER)
			.extracting(p -> p.getStatus())
			.containsExactlyInAnyOrder(ParticipationStatus.CANCELED, ParticipationStatus.PENDING);
	}

	@Test
	@DisplayName("파티장이 나가면 가장 먼저 합류한 파티원이 파티장이 되고, 마지막 사람이 나가면 파티가 종료된다")
	void leaderLeaves() throws Exception {
		String token = token(create(LEADER, COURSE_10K));
		long partyId = partyRepository.findAll().getFirst().getId();
		join(MEMBER, token).andExpect(status().isCreated());
		join(OTHER, token).andExpect(status().isCreated());

		leave(LEADER, partyId).andExpect(status().isOk());
		assertThat(partyRepository.findById(partyId).orElseThrow().getLeaderMemberId()).isEqualTo(MEMBER);

		leave(OTHER, partyId).andExpect(status().isOk());
		leave(MEMBER, partyId).andExpect(status().isOk());
		Party closed = partyRepository.findById(partyId).orElseThrow();
		assertThat(closed.getStatus()).isEqualTo(PartyStatus.CLOSED);
		assertThat(closed.getClosedAt()).isNotNull();

		join(LEADER, token)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_NOT_RECRUITING"))
			.andExpect(jsonPath("$.data.partyId").value(partyId))
			.andExpect(jsonPath("$.data.partyStatus").value("CLOSED"));
		// 파티원이 아니면 상태를 알려 주지 않고 403
		leave(LEADER, partyId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_NOT_MEMBER"));
	}

	@Test
	@DisplayName("파티원 신청서로는 개인 주문([결제하기])을 만들 수 없다 (크루 몫을 우회해 개인 자리를 잡지 않게)")
	void partyParticipationIsNotOrderedIndividually() throws Exception {
		create(LEADER, COURSE_10K).andExpect(status().isCreated());
		long participationId = participationRepository.findAll().getFirst().getId();

		mockMvc.perform(post("/api/v1/order/orders")
				.header("X-Member-Id", LEADER)
				.header("Idempotency-Key", "party-order-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"participationId\": %d }".formatted(participationId)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("파티원 신청은 파티장이 신청한 뒤 파티 결제로 결제해 주세요."));
	}

	@Test
	@DisplayName("파티원 신청서는 개인 신청 취소로 취소할 수 없고 파티 나가기로만 끝난다 (파티원 줄만 남지 않게)")
	void partyParticipationIsNotCanceledIndividually() throws Exception {
		create(LEADER, COURSE_10K).andExpect(status().isCreated());
		long participationId = participationRepository.findAll().getFirst().getId();

		mockMvc.perform(post("/api/v1/order/participations/" + participationId + "/cancel")
				.header("X-Member-Id", LEADER))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("파티원 신청은 파티 나가기로 취소해 주세요."));
		assertThat(participationRepository.findById(participationId).orElseThrow().getStatus())
			.isEqualTo(ParticipationStatus.PENDING);
	}

	@Test
	@DisplayName("같은 사람이 합류를 동시에 두 번 보내면 하나만 합류되고 나머지는 409다 (500이 아님)")
	void concurrentDoubleJoin() throws Exception {
		String token = token(create(LEADER, COURSE_10K));
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> results = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			results.add(pool.submit(() -> {
				start.await();
				return join(MEMBER, token).andReturn().getResponse().getStatus();
			}));
		}
		start.countDown();
		List<Integer> statuses = new ArrayList<>();
		for (Future<Integer> result : results) {
			statuses.add(result.get(10, TimeUnit.SECONDS));
		}
		pool.shutdown();

		assertThat(statuses).containsExactlyInAnyOrder(201, 409);
		assertThat(partyMemberRepository.findAll()).filteredOn(row -> row.getMemberId() == MEMBER).hasSize(1);
	}

	@Test
	@DisplayName("내 파티 상태: 파티장은 4명 이상 모집 중일 때만 신청할 수 있고, 파티원도 자기 파티를 보며, 결제 현황을 알려 준다")
	void raceStatus() throws Exception {
		raceStatus(LEADER, RACE_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.role").value("NONE"))
			.andExpect(jsonPath("$.data.canApply").value(false))
			.andExpect(jsonPath("$.data.reason").value("NO_PARTY"))
			.andExpect(jsonPath("$.data.partyId").doesNotExist());

		String token = token(create(LEADER, COURSE_HALF));
		long partyId = partyRepository.findAll().getFirst().getId();
		join(MEMBER, token).andExpect(status().isCreated());

		// 2명이라 최소 인원(4명)이 모자란다
		raceStatus(LEADER, RACE_ID)
			.andExpect(jsonPath("$.message").value("이 대회 내 파티 상태를 조회했습니다."))
			.andExpect(jsonPath("$.data.role").value("LEADER"))
			.andExpect(jsonPath("$.data.canApply").value(false))
			.andExpect(jsonPath("$.data.reason").value("TOO_FEW_MEMBERS"))
			.andExpect(jsonPath("$.data.partyId").value(partyId))
			.andExpect(jsonPath("$.data.raceCourseId").value(COURSE_HALF))
			.andExpect(jsonPath("$.data.partyStatus").value("RECRUITING"))
			.andExpect(jsonPath("$.data.memberCount").value(2))
			.andExpect(jsonPath("$.data.minCount").value(4))
			.andExpect(jsonPath("$.data.maxCount").value(10))
			.andExpect(jsonPath("$.data.paidCount").value(0))
			.andExpect(jsonPath("$.data.myPaid").value(false));
		raceStatus(MEMBER, RACE_ID)
			.andExpect(jsonPath("$.data.role").value("MEMBER"))
			.andExpect(jsonPath("$.data.canApply").value(false))
			.andExpect(jsonPath("$.data.reason").value("NOT_LEADER"))
			.andExpect(jsonPath("$.data.partyId").value(partyId));
		raceStatus(OTHER, RACE_ID).andExpect(jsonPath("$.data.role").value("NONE"));
		raceStatus(LEADER, RACE_ID + 1).andExpect(jsonPath("$.data.role").value("NONE"));

		join(OTHER, token).andExpect(status().isCreated());
		join(FOURTH, token).andExpect(status().isCreated());
		raceStatus(LEADER, RACE_ID)
			.andExpect(jsonPath("$.data.canApply").value(true))
			.andExpect(jsonPath("$.data.reason").doesNotExist())
			.andExpect(jsonPath("$.data.memberCount").value(4));

		// 파티 신청(BUK-37) 전이라 상태를 직접 바꾼다. 파티원 하나가 결제를 마쳤다
		jdbcTemplate.update("update order_party set status = 'PAYING', payment_due_at = ? where id = ?",
			LocalDateTime.of(2026, 11, 7, 10, 0), partyId);
		jdbcTemplate.update("update order_participation set status = 'CONFIRMED' where party_id = ? and member_id = ?",
			partyId, MEMBER);
		raceStatus(LEADER, RACE_ID)
			.andExpect(jsonPath("$.data.canApply").value(false))
			.andExpect(jsonPath("$.data.reason").value("NOT_RECRUITING"))
			.andExpect(jsonPath("$.data.partyStatus").value("PAYING"))
			.andExpect(jsonPath("$.data.paidCount").value(1))
			.andExpect(jsonPath("$.data.paymentDueAt").value("2026-11-07T10:00:00"))
			.andExpect(jsonPath("$.data.myPaid").value(false));
		raceStatus(MEMBER, RACE_ID).andExpect(jsonPath("$.data.myPaid").value(true));
		jdbcTemplate.update("update order_party set status = 'PAID' where id = ?", partyId);
		raceStatus(OTHER, RACE_ID).andExpect(jsonPath("$.data.partyStatus").value("PAID"));

		// 파티장이 나가 넘겨받으면 새 파티장이 파티장이다. 나간 사람·종료된 파티는 보지 않는다
		jdbcTemplate.update("update order_party set status = 'RECRUITING' where id = ?", partyId);
		jdbcTemplate.update("update order_participation set status = 'PENDING' where party_id = ?", partyId);
		leave(LEADER, partyId).andExpect(status().isOk());
		raceStatus(LEADER, RACE_ID).andExpect(jsonPath("$.data.role").value("NONE"));
		raceStatus(MEMBER, RACE_ID).andExpect(jsonPath("$.data.role").value("LEADER"));
		jdbcTemplate.update("update order_party set status = 'CLOSED' where id = ?", partyId);
		raceStatus(MEMBER, RACE_ID).andExpect(jsonPath("$.data.role").value("NONE"));
	}

	@Test
	@DisplayName("내 파티 상태: 대회 ID가 없거나 0 이하면 400, 회원 ID 헤더가 없으면 401")
	void raceStatusInvalid() throws Exception {
		mockMvc.perform(get(RACE_STATUS_URL).header("X-Member-Id", LEADER))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("필수 요청 값(raceId)이 없습니다."));
		raceStatus(LEADER, 0)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("대회 ID가 올바르지 않습니다."));
		mockMvc.perform(get(RACE_STATUS_URL).param("raceId", String.valueOf(RACE_ID)))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions raceStatus(long memberId, long raceId) throws Exception {
		return mockMvc.perform(get(RACE_STATUS_URL)
			.header("X-Member-Id", memberId)
			.param("raceId", String.valueOf(raceId)));
	}

	private ResultActions create(long memberId, long raceCourseId) throws Exception {
		return mockMvc.perform(post(BASE_URL)
			.header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{ "crewId": %d, "raceCourseId": %d, "participant": %s, "thirdPartyConsent": true }
				""".formatted(CREW_ID, raceCourseId, participant())));
	}

	private ResultActions join(long memberId, String token) throws Exception {
		return mockMvc.perform(post(BASE_URL + "/join")
			.header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{ "joinToken": "%s", "participant": %s, "thirdPartyConsent": true }
				""".formatted(token, participant())));
	}

	private ResultActions regenerate(long memberId, long partyId) throws Exception {
		return mockMvc.perform(post(BASE_URL + "/" + partyId + "/join-link").header("X-Member-Id", memberId));
	}

	private ResultActions leave(long memberId, long partyId) throws Exception {
		return mockMvc.perform(post(BASE_URL + "/" + partyId + "/leave").header("X-Member-Id", memberId));
	}

	private static String token(ResultActions created) throws Exception {
		return JsonPath.read(created.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString(), "$.data.party.joinToken");
	}

	private static String body(long raceCourseId) {
		return """
			{ "raceCourseId": %d, "participant": %s, "thirdPartyConsent": true }
			""".formatted(raceCourseId, participant());
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
