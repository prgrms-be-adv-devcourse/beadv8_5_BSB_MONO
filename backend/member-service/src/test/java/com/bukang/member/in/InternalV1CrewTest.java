package com.bukang.member.in;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bukang.member.app.MemberFacade;
import com.bukang.member.crew.app.CrewFacade;
import com.bukang.member.crew.out.CrewInviteRepository;
import com.bukang.member.crew.out.CrewMemberRepository;
import com.bukang.member.crew.out.CrewRepository;
import com.bukang.member.domain.TermsType;
import com.bukang.member.dto.MemberJoinRequestDto;
import com.bukang.member.out.MemberKafkaPublisher;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.TermsAgreementRepository;

// 파티 서비스가 부르는 크루 내부 API: 크루원 여부·가입 시각, 크루원 수
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalV1CrewTest {
	private static final String MEMBER_STATUS = "/internal/v1/member/crews/{crewId}/members/{memberId}";
	private static final String MEMBER_COUNT = "/internal/v1/member/crews/{crewId}/member-count";

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

	// 브로커가 없어 가입 이벤트를 보낼 때마다 기다리지 않도록 발행기를 목으로 바꾼다
	@MockitoBean
	private MemberKafkaPublisher memberKafkaPublisher;

	private long leaderId;
	private long memberId;
	private long outsiderId;
	private long crewId;

	@BeforeEach
	void setUp() {
		leaderId = join("leader@crewrun.com", "새벽러너", "010-1000-0001");
		memberId = join("member@crewrun.com", "주말10K", "010-1000-0002");
		outsiderId = join("outsider@crewrun.com", "구경꾼", "010-1000-0003");
		crewId = crewFacade.createCrew(leaderId, "새벽한강크루").crewId();
		String token = crewFacade.createInvite(leaderId, crewId).inviteToken();
		crewFacade.join(memberId, token);
	}

	@AfterEach
	void tearDown() {
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

	private long join(String email, String nickname, String phone) {
		return memberFacade.join(new MemberJoinRequestDto(
			email, "password1234", "김러너", nickname, phone, LocalDate.of(1995, 4, 12), null,
			List.of(TermsType.SERVICE, TermsType.PRIVACY)
		)).getId();
	}
}
