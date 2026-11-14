package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.bukang.member.app.MemberFacade;
import com.bukang.member.crew.app.CrewFacade;
import com.bukang.member.crew.out.CrewInviteRepository;
import com.bukang.member.crew.out.CrewMemberRepository;
import com.bukang.member.crew.out.CrewRepository;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.support.MemberFixture;
import com.jayway.jsonpath.JsonPath;

// 파티 서비스가 부르는 크루 내부 API: 크루원 여부·가입 시각, 크루원 수, 파티 만들기 제한 (BUK-187)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InMemoryIdentityVerificationStore.Config.class)
class InternalV1CrewTest {
	@Autowired
	private InMemoryIdentityVerificationStore identityStore;

	private static final String MEMBER_STATUS = "/internal/v1/member/crews/{crewId}/members/{memberId}";
	private static final String MEMBER_COUNT = "/internal/v1/member/crews/{crewId}/member-count";
	private static final String PENALTY = "/internal/v1/member/crews/{crewId}/penalty";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private MemberFacade memberFacade;
	@Autowired
	private CrewFacade crewFacade;
	@Autowired
	private MemberRepository memberRepository;
	@Autowired
	private TermsAgreementRepository termsAgreementRepository;
	@Autowired
	private CrewRepository crewRepository;
	@Autowired
	private CrewMemberRepository crewMemberRepository;
	@Autowired
	private CrewInviteRepository crewInviteRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	private long leaderId;
	private long memberId;
	private long outsiderId;
	private long crewId;

	@BeforeEach
	void setUp() {
		leaderId = memberFacade.join(MemberFixture.joinRequest().build(identityStore)).getId();
		memberId = memberFacade.join(MemberFixture.joinRequest().build(identityStore)).getId();
		outsiderId = memberFacade.join(MemberFixture.joinRequest().build(identityStore)).getId();
		crewId = crewFacade.createCrew(leaderId, "새벽한강크루").crewId();
		String token = crewFacade.createInvite(leaderId, crewId).inviteToken();
		crewFacade.join(memberId, token);
	}

	@AfterEach
	void tearDown() {
		jdbcTemplate.update("delete from member_crew_penalty");
		crewInviteRepository.deleteAll();
		crewMemberRepository.deleteAll();
		crewRepository.deleteAll();
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("크루원이면 crewMember가 true이고 역할과 가입 시각을 돌려준다")
	void crewMember() throws Exception {
		mockMvc.perform(get(MEMBER_STATUS, crewId, memberId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("크루원 여부를 조회했습니다."))
			.andExpect(jsonPath("$.data.crewId").value(crewId))
			.andExpect(jsonPath("$.data.memberId").value(memberId))
			.andExpect(jsonPath("$.data.crewMember").value(true))
			.andExpect(jsonPath("$.data.role").value("MEMBER"))
			.andExpect(jsonPath("$.data.joinedAt").isNotEmpty());
		mockMvc.perform(get(MEMBER_STATUS, crewId, leaderId))
			.andExpect(jsonPath("$.data.role").value("LEADER"));
	}

	@Test
	@DisplayName("크루원이 아니어도 200이고 crewMember가 false, role과 joinedAt은 null이다")
	void notCrewMember() throws Exception {
		mockMvc.perform(get(MEMBER_STATUS, crewId, outsiderId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.crewMember").value(false))
			.andExpect(jsonPath("$.data.role").isEmpty())
			.andExpect(jsonPath("$.data.joinedAt").isEmpty());
	}

	@Test
	@DisplayName("크루가 없거나 없앤 크루면 두 API 모두 404 CREW_NOT_FOUND이다")
	void crewNotFound() throws Exception {
		mockMvc.perform(get(MEMBER_STATUS, crewId + 999, memberId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CREW_NOT_FOUND"))
			.andExpect(jsonPath("$.message").value("존재하지 않는 크루입니다."));
		mockMvc.perform(get(MEMBER_COUNT, crewId + 999))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CREW_NOT_FOUND"));

		// 없앤 크루도 없는 크루로 본다 (없애는 API는 2그룹이라 상태를 직접 바꾼다)
		jdbcTemplate.update("update member_crew set status = 'DISBANDED' where crew_id = ?", crewId);
		mockMvc.perform(get(MEMBER_STATUS, crewId, memberId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CREW_NOT_FOUND"));
		mockMvc.perform(get(MEMBER_COUNT, crewId))
			.andExpect(status().isNotFound());
		// 공개 API도 같다
		mockMvc.perform(get("/api/v1/member/crews/{crewId}", crewId).header("X-Member-Id", leaderId))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/member/crews/{crewId}/invites", crewId).header("X-Member-Id", leaderId))
			.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("크루원 수는 모든 역할을 센다")
	void memberCount() throws Exception {
		jdbcTemplate.update("update member_crew_member set role = 'MANAGER' where member_id = ?", memberId);

		mockMvc.perform(get(MEMBER_COUNT, crewId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("크루원 수를 조회했습니다."))
			.andExpect(jsonPath("$.data.crewId").value(crewId))
			.andExpect(jsonPath("$.data.memberCount").value(2));
	}

	@Test
	@DisplayName("크루원 수 응답에 크루 생성 시각이 들어 있다")
	void memberCountHasCrewCreatedAt() throws Exception {
		String body = mockMvc.perform(get(MEMBER_COUNT, crewId))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		String createdAt = JsonPath.read(body, "$.data.crewCreatedAt");
		assertThat(LocalDateTime.parse(createdAt))
			.isEqualTo(crewRepository.findById(crewId).orElseThrow().getCreatedAt());
	}

	@Test
	@DisplayName("제한 기간 안이면 restricted가 true이고, 기간이 겹치는 행이 여럿이면 가장 늦게 끝나는 시각을 준다")
	void penaltyRestricted() throws Exception {
		LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
		insertPenalty(crewId, "2026-10", now.minusDays(20), now.plusDays(10));
		insertPenalty(crewId, "2026-11", now.minusDays(1), now.plusDays(29));

		mockMvc.perform(get(PENALTY, crewId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("크루 파티 만들기 제한을 조회했습니다."))
			.andExpect(jsonPath("$.data.restricted").value(true))
			.andExpect(jsonPath("$.data.endAt").value(iso(now.plusDays(29))));
	}

	@Test
	@DisplayName("제한 행이 없거나, 끝났거나, 아직 시작 전이면 restricted가 false이고 endAt은 null이다")
	void penaltyNotRestricted() throws Exception {
		mockMvc.perform(get(PENALTY, crewId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.restricted").value(false))
			.andExpect(jsonPath("$.data.endAt").isEmpty());

		LocalDateTime now = LocalDateTime.now();
		// 끝나는 시각은 제한에 들지 않는다 (start_at <= 지금 < end_at)
		insertPenalty(crewId, "2026-09", now.minusDays(40), now.minusDays(10));
		insertPenalty(crewId, "2026-10", now.minusDays(30), now.minusSeconds(1));
		insertPenalty(crewId, "2026-11", now.plusDays(1), now.plusDays(31));

		mockMvc.perform(get(PENALTY, crewId))
			.andExpect(jsonPath("$.data.restricted").value(false))
			.andExpect(jsonPath("$.data.endAt").isEmpty());
	}

	@Test
	@DisplayName("크루가 없어도 404가 아니라 200이고 restricted가 false이다")
	void penaltyUnknownCrew() throws Exception {
		mockMvc.perform(get(PENALTY, crewId + 999))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.restricted").value(false))
			.andExpect(jsonPath("$.data.endAt").isEmpty());
	}

	@Test
	@DisplayName("크루 홈의 partyRestrictedUntil은 제한 중이면 끝나는 시각, 아니면 null이다")
	void crewHomePartyRestrictedUntil() throws Exception {
		mockMvc.perform(get("/api/v1/member/crews/{crewId}", crewId).header("X-Member-Id", memberId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.partyRestrictedUntil").isEmpty());

		LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
		insertPenalty(crewId, "2026-11", now.minusDays(1), now.plusDays(29));

		mockMvc.perform(get("/api/v1/member/crews/{crewId}", crewId).header("X-Member-Id", memberId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.partyRestrictedUntil").value(iso(now.plusDays(29))))
			.andExpect(jsonPath("$.data.memberCount").value(2));
	}

	// JSON의 LocalDateTime 형식 (초가 0이어도 초까지 쓴다)
	private static String iso(LocalDateTime time) {
		return time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
	}

	private void insertPenalty(long targetCrewId, String month, LocalDateTime startAt, LocalDateTime endAt) {
		jdbcTemplate.update("""
			insert into member_crew_penalty
			(crew_id, penalty_month, low_payment_count, start_at, end_at, created_at, updated_at)
			values (?, ?, 3, ?, ?, ?, ?)
			""", targetCrewId, month, startAt, endAt, startAt, startAt);
	}
}
