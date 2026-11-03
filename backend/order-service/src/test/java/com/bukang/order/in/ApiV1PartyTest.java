package com.bukang.order.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

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
	private static final long LEADER = 1;
	private static final long MEMBER = 2;
	private static final long OTHER = 3;
	private static final long CREW_ID = 7;
	private static final long COURSE_10K = 10;
	private static final long COURSE_HALF = 11;
	private static final long OTHER_RACE_COURSE = 20;
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
		raceCoursePort.put(StubRaceCoursePort.openCourse(OTHER_RACE_COURSE, 200, 14, null));
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
		join(OTHER, token, COURSE_10K)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORDER_CREW_MEMBER_REQUIRED"));
	}

	@Test
	@DisplayName("파티원은 같은 대회의 다른 종목을 골라 합류한다. 다른 대회 종목은 고를 수 없다")
	void join() throws Exception {
		String token = token(create(LEADER, COURSE_10K));

		join(MEMBER, token, COURSE_HALF)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.participationType").value("PARTY"))
			.andExpect(jsonPath("$.data.raceCourseId").value(COURSE_HALF));
		join(OTHER, token, OTHER_RACE_COURSE)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_RACE_MISMATCH"));
		join(MEMBER, token, COURSE_HALF)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_ALREADY_JOINED"));
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

		join(MEMBER, token, COURSE_10K)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_INDIVIDUAL_PARTICIPATION_EXISTS"))
			.andExpect(jsonPath("$.data.participationId").value(individualId));

		mockMvc.perform(post("/api/v1/order/participations/" + individualId + "/cancel").header("X-Member-Id", MEMBER))
			.andExpect(status().isOk());
		join(MEMBER, token, COURSE_10K).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("파티에 있는 사람은 같은 대회에 개인 신청도, 다른 파티 합류도 할 수 없다")
	void onePartyPerRace() throws Exception {
		String first = token(create(LEADER, COURSE_10K));
		String second = token(create(OTHER, COURSE_10K));
		join(MEMBER, first, COURSE_10K).andExpect(status().isCreated());

		join(MEMBER, second, COURSE_10K)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_DUPLICATE_PARTICIPATION"));
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
		join(MEMBER, oldToken, COURSE_10K)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_NOT_FOUND"));

		jdbcTemplate.update("update order_party set join_token_issued_at = ? where id = ?",
			LocalDateTime.now().minusHours(24).minusMinutes(1), partyId);
		join(MEMBER, newToken, COURSE_10K)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_LINK_EXPIRED"));
	}

	@Test
	@DisplayName("나간 사람은 신청서가 취소되고, 같은 링크로 다시 합류하면 파티원 줄 하나가 되살아난다")
	void leaveAndRejoin() throws Exception {
		String token = token(create(LEADER, COURSE_10K));
		long partyId = partyRepository.findAll().getFirst().getId();
		join(MEMBER, token, COURSE_10K).andExpect(status().isCreated());

		leave(MEMBER, partyId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("CANCELED"));
		leave(MEMBER, partyId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_NOT_MEMBER"));
		join(MEMBER, token, COURSE_HALF).andExpect(status().isCreated());

		assertThat(partyMemberRepository.findAll()).filteredOn(row -> row.getMemberId() == MEMBER)
			.singleElement().satisfies(row -> assertThat(row.isActive()).isTrue());
		assertThat(participationRepository.findAll()).filteredOn(p -> p.getMemberId() == MEMBER)
			.extracting(p -> p.getStatus())
			.containsExactlyInAnyOrder(ParticipationStatus.CANCELED, ParticipationStatus.PENDING);
	}

	@Test
	@DisplayName("파티장이 나가면 가장 먼저 합류한 파티원이 파티장이 되고, 마지막 사람이 나가면 파티가 종료된다")
	void leaderLeaves() throws Exception {
		String token = token(create(LEADER, COURSE_10K));
		long partyId = partyRepository.findAll().getFirst().getId();
		join(MEMBER, token, COURSE_10K).andExpect(status().isCreated());
		join(OTHER, token, COURSE_10K).andExpect(status().isCreated());

		leave(LEADER, partyId).andExpect(status().isOk());
		assertThat(partyRepository.findById(partyId).orElseThrow().getLeaderMemberId()).isEqualTo(MEMBER);

		leave(OTHER, partyId).andExpect(status().isOk());
		leave(MEMBER, partyId).andExpect(status().isOk());
		Party closed = partyRepository.findById(partyId).orElseThrow();
		assertThat(closed.getStatus()).isEqualTo(PartyStatus.CLOSED);
		assertThat(closed.getClosedAt()).isNotNull();

		join(LEADER, token, COURSE_10K)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_NOT_RECRUITING"));
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

	private ResultActions create(long memberId, long raceCourseId) throws Exception {
		return mockMvc.perform(post(BASE_URL)
			.header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{ "crewId": %d, "raceCourseId": %d, "participant": %s, "thirdPartyConsent": true }
				""".formatted(CREW_ID, raceCourseId, participant())));
	}

	private ResultActions join(long memberId, String token, long raceCourseId) throws Exception {
		return mockMvc.perform(post(BASE_URL + "/join")
			.header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{ "joinToken": "%s", "raceCourseId": %d, "participant": %s, "thirdPartyConsent": true }
				""".formatted(token, raceCourseId, participant())));
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
