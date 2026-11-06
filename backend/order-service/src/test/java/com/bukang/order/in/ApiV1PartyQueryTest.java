package com.bukang.order.in;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.bukang.order.out.OrderOutboxKafkaEventRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.PartyMemberRepository;
import com.bukang.order.out.PartyRepository;
import com.bukang.order.support.StubCrewPort;
import com.bukang.order.support.StubCrewPortConfig;
import com.bukang.order.support.StubMemberPorts;
import com.bukang.order.support.StubMemberPortsConfig;
import com.bukang.order.support.StubRaceCoursePort;
import com.bukang.order.support.StubRaceCoursePortConfig;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubCrewPortConfig.class, StubMemberPortsConfig.class})
class ApiV1PartyQueryTest {
	private static final String BASE_URL = "/api/v1/order/parties";
	private static final long LEADER = 1;
	private static final long MEMBER = 2;
	private static final long OTHER = 3;
	private static final long CREW_ID = 7;
	private static final long COURSE = 10;
	private static final long OTHER_COURSE = 20;
	private static final long RACE_ID = 100;
	private static final long OTHER_RACE_ID = 200;

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

	@Autowired
	private StubMemberPorts memberPorts;

	@BeforeEach
	void setUp() {
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE, RACE_ID, 14, null));
		raceCoursePort.put(StubRaceCoursePort.openCourse(OTHER_COURSE, OTHER_RACE_ID, 14, null));
		// MEMBER는 닉네임을 정하지 않는다 = 회원 서비스 응답에 없는 회원(탈퇴)
		memberPorts.member("파티장", LEADER);
		memberPorts.member("남", OTHER);
	}

	@AfterEach
	void tearDown() {
		participationRepository.deleteAll();
		partyMemberRepository.deleteAll();
		partyRepository.deleteAll();
		orderOutboxKafkaEventRepository.deleteAll();
		raceCoursePort.clear();
		crewPort.clear();
		memberPorts.clear();
	}

	@Test
	@DisplayName("파티원은 상세를 본다. 결제 인원은 신청서에서 세고, 토큰은 파티장에게만, 닉네임이 없으면 「탈퇴한 회원」")
	void detail() throws Exception {
		String token = create(LEADER, COURSE);
		long partyId = partyId();
		join(MEMBER, token);
		confirm(LEADER);

		detail(LEADER, partyId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.raceCourseId").value(COURSE))
			.andExpect(jsonPath("$.data.joinToken").value(token))
			.andExpect(jsonPath("$.data.memberCount").value(2))
			.andExpect(jsonPath("$.data.paidCount").value(1))
			.andExpect(jsonPath("$.data.members[0].memberId").value(LEADER))
			.andExpect(jsonPath("$.data.members[0].nickname").value("파티장"))
			.andExpect(jsonPath("$.data.members[0].leader").value(true))
			.andExpect(jsonPath("$.data.members[0].paid").value(true))
			.andExpect(jsonPath("$.data.members[1].nickname").value("탈퇴한 회원"))
			.andExpect(jsonPath("$.data.members[1].participationStatus").value("PENDING"))
			.andExpect(jsonPath("$.data.members[1].paid").value(false))
			.andExpect(jsonPath("$.data.members[1].participant").doesNotExist());

		detail(MEMBER, partyId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.joinToken").doesNotExist());
	}

	@Test
	@DisplayName("파티원이 아니거나 나간 사람은 상세를 볼 수 없고(403), 없는 파티는 404")
	void detailForbidden() throws Exception {
		String token = create(LEADER, COURSE);
		long partyId = partyId();
		join(MEMBER, token);
		mockMvc.perform(post(BASE_URL + "/" + partyId + "/leave").header("X-Member-Id", MEMBER))
			.andExpect(status().isOk());

		detail(OTHER, partyId).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_NOT_MEMBER"));
		detail(MEMBER, partyId).andExpect(status().isForbidden());
		detail(LEADER, partyId + 999).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_NOT_FOUND"));
		detail(LEADER, partyId).andExpect(jsonPath("$.data.memberCount").value(1));
	}

	@Test
	@DisplayName("다시 합류한 사람은 가장 최근 신청서 상태로 보이고, 나갈 때 취소된 옛 신청서는 세지 않는다")
	void rejoinUsesLatestParticipation() throws Exception {
		String token = create(LEADER, COURSE);
		long partyId = partyId();
		join(MEMBER, token);
		mockMvc.perform(post(BASE_URL + "/" + partyId + "/leave").header("X-Member-Id", MEMBER))
			.andExpect(status().isOk());
		join(MEMBER, token);

		detail(LEADER, partyId)
			.andExpect(jsonPath("$.data.memberCount").value(2))
			.andExpect(jsonPath("$.data.members[1].participationStatus").value("PENDING"));
	}

	@Test
	@DisplayName("내 파티는 지금 들어 있는 파티만, raceId를 주면 그 대회만 준다")
	void mine() throws Exception {
		create(LEADER, COURSE);
		String otherToken = create(OTHER, OTHER_COURSE);
		join(LEADER, otherToken);

		mine(LEADER, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].raceId").value(OTHER_RACE_ID))
			.andExpect(jsonPath("$.data[0].leaderNickname").value("남"))
			.andExpect(jsonPath("$.data[0].myParty").value(true))
			.andExpect(jsonPath("$.data[0].joinToken").doesNotExist());
		mine(LEADER, RACE_ID)
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].raceCourseId").value(COURSE));
		mine(MEMBER, null).andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	@DisplayName("링크 미리 보기: 없는·옛 토큰은 404, 그 밖은 200에 상태와 링크 살아 있음. 명단·토큰은 주지 않는다")
	void preview() throws Exception {
		String token = create(LEADER, COURSE);
		long partyId = partyId();

		preview(OTHER, token)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(partyId))
			.andExpect(jsonPath("$.data.status").value("RECRUITING"))
			.andExpect(jsonPath("$.data.leaderNickname").value("파티장"))
			.andExpect(jsonPath("$.data.memberCount").value(1))
			.andExpect(jsonPath("$.data.joinLinkAlive").value(true))
			.andExpect(jsonPath("$.data.joinToken").doesNotExist())
			.andExpect(jsonPath("$.data.members").doesNotExist());

		// 링크가 끝나고 결제 진행 중이어도 200으로 상태를 알려 준다
		jdbcTemplate.update("update order_party set status = 'PAYING', join_token_issued_at = ? where id = ?",
			LocalDateTime.now().minusHours(25), partyId);
		preview(OTHER, token)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("PAYING"))
			.andExpect(jsonPath("$.data.joinLinkAlive").value(false));

		preview(OTHER, "no-such-token")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ORDER_PARTY_NOT_FOUND"));
		preview(OTHER, "")
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("링크를 다시 만들면 옛 토큰 미리 보기는 404")
	void previewOldToken() throws Exception {
		String token = create(LEADER, COURSE);
		mockMvc.perform(post(BASE_URL + "/" + partyId() + "/join-link").header("X-Member-Id", LEADER))
			.andExpect(status().isOk());

		preview(OTHER, token).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("크루 홈: 크루원에게 모집 중·결제 진행 중 파티를 모두 준다. 크루원이 아니면 403, 없는 크루는 404")
	void crewActive() throws Exception {
		create(LEADER, COURSE);
		long recruiting = partyId();
		create(OTHER, OTHER_COURSE);
		long closed = partyRepository.findAll().stream().mapToLong(p -> p.getId()).max().orElseThrow();
		jdbcTemplate.update("update order_party set status = 'CLOSED' where id = ?", closed);

		crewActive(MEMBER)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].id").value(recruiting))
			.andExpect(jsonPath("$.data[0].leaderNickname").value("파티장"))
			.andExpect(jsonPath("$.data[0].memberCount").value(1))
			.andExpect(jsonPath("$.data[0].myParty").value(false))
			.andExpect(jsonPath("$.data[0].joinToken").doesNotExist());
		crewActive(LEADER).andExpect(jsonPath("$.data[0].myParty").value(true));

		crewPort.missingCrew(CREW_ID + 1);
		mockMvc.perform(get(BASE_URL + "/crews/" + (CREW_ID + 1) + "/active").header("X-Member-Id", MEMBER))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ORDER_CREW_NOT_FOUND"));

		crewPort.notCrewMember(MEMBER);
		crewActive(MEMBER)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORDER_CREW_MEMBER_REQUIRED"));
	}

	@Test
	@DisplayName("회원 서비스에 닉네임을 묻지 못하면 503")
	void nicknameUnavailable() throws Exception {
		create(LEADER, COURSE);
		memberPorts.setUnavailable(true);

		detail(LEADER, partyId())
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("ORDER_EXTERNAL_SERVICE_UNAVAILABLE"));
	}

	private void confirm(long memberId) {
		jdbcTemplate.update("update order_participation set status = 'CONFIRMED' where member_id = ? "
			+ "and party_id is not null", memberId);
	}

	private long partyId() {
		return partyRepository.findAll().stream().mapToLong(p -> p.getId()).min().orElseThrow();
	}

	private ResultActions detail(long memberId, long partyId) throws Exception {
		return mockMvc.perform(get(BASE_URL + "/" + partyId).header("X-Member-Id", memberId));
	}

	private ResultActions mine(long memberId, Long raceId) throws Exception {
		String query = raceId == null ? "" : "?raceId=" + raceId;
		return mockMvc.perform(get(BASE_URL + "/me" + query).header("X-Member-Id", memberId));
	}

	private ResultActions preview(long memberId, String token) throws Exception {
		return mockMvc.perform(post(BASE_URL + "/preview")
			.header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"joinToken\": \"%s\" }".formatted(token)));
	}

	private ResultActions crewActive(long memberId) throws Exception {
		return mockMvc.perform(get(BASE_URL + "/crews/" + CREW_ID + "/active").header("X-Member-Id", memberId));
	}

	private String create(long memberId, long raceCourseId) throws Exception {
		String body = mockMvc.perform(post(BASE_URL)
				.header("X-Member-Id", memberId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "crewId": %d, "raceCourseId": %d, "participant": %s, "thirdPartyConsent": true }
					""".formatted(CREW_ID, raceCourseId, participant())))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.data.party.joinToken");
	}

	private void join(long memberId, String token) throws Exception {
		mockMvc.perform(post(BASE_URL + "/join")
				.header("X-Member-Id", memberId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "joinToken": "%s", "participant": %s, "thirdPartyConsent": true }
					""".formatted(token, participant())))
			.andExpect(status().isCreated());
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
