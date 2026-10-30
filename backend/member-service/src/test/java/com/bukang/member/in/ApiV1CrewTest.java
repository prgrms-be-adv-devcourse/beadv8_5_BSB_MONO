package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.common.shared.member.web.MemberIdHeader;
import com.bukang.member.app.MemberFacade;
import com.bukang.member.crew.out.CrewInviteRepository;
import com.bukang.member.crew.out.CrewMemberRepository;
import com.bukang.member.crew.out.CrewRepository;
import com.bukang.member.domain.TermsType;
import com.bukang.member.dto.MemberJoinRequestDto;
import com.bukang.member.out.MemberKafkaPublisher;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.jayway.jsonpath.JsonPath;

// 크루 만들기·조회, 초대 링크 만들기·취소·가입 (BE-027~030). X-Member-Id 헤더는 테스트에서 직접 넣는다
// 시간은 Clock 목으로 바꿔 24시간 지난 링크를 만든다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiV1CrewTest {
	private static final String CREWS = "/api/v1/member/crews";
	private static final String MY_CREWS = "/api/v1/member/crews/me";
	private static final String CREW = "/api/v1/member/crews/{crewId}";
	private static final String INVITES = "/api/v1/member/crews/{crewId}/invites";
	private static final String INVITE = "/api/v1/member/crews/{crewId}/invites/{inviteId}";
	private static final String INVITE_BY_TOKEN = "/api/v1/member/crews/invites/{inviteToken}";
	private static final String JOIN = "/api/v1/member/crews/invites/{inviteToken}/join";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private MemberFacade memberFacade;
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

	@MockitoBean
	private Clock clock;

	private Instant now;
	private long leaderId;
	private long otherId;
	private long outsiderId;

	@BeforeEach
	void setUp() {
		now = Instant.now();
		given(clock.instant()).willAnswer(invocation -> now);
		leaderId = join("leader@crewrun.com", "새벽러너", "010-1000-0001");
		otherId = join("other@crewrun.com", "주말10K", "010-1000-0002");
		outsiderId = join("outsider@crewrun.com", "구경꾼", "010-1000-0003");
	}

	@AfterEach
	void tearDown() {
		// 초대·크루원이 크루를 참조하므로(FK) 크루보다 먼저 지운다
		crewInviteRepository.deleteAll();
		crewMemberRepository.deleteAll();
		crewRepository.deleteAll();
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("크루를 만들면 201이고 만든 회원이 크루장이며, 크루 홈에 크루장이 보인다")
	void createThenFindHome() throws Exception {
		long crewId = createCrew(leaderId, "새벽한강크루");

		mockMvc.perform(get(CREW, crewId).header(MemberIdHeader.NAME, leaderId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.crewId").value(crewId))
			.andExpect(jsonPath("$.data.name").value("새벽한강크루"))
			.andExpect(jsonPath("$.data.myRole").value("LEADER"))
			.andExpect(jsonPath("$.data.memberCount").value(1))
			.andExpect(jsonPath("$.data.members[0].memberId").value(leaderId))
			.andExpect(jsonPath("$.data.members[0].nickname").value("새벽러너"))
			.andExpect(jsonPath("$.data.members[0].role").value("LEADER"));
		// 크루장의 가입 시각은 크루를 만든 시각과 같다
		Long sameTime = jdbcTemplate.queryForObject(
			"select count(*) from member_crew_member m join member_crew c on c.crew_id = m.crew_id"
				+ " where m.joined_at = c.created_at", Long.class);
		assertThat(sameTime).isEqualTo(1);
	}

	@Test
	@DisplayName("크루 이름은 앞뒤 공백을 빼고 저장하고, 비었거나 20자를 넘으면 400이다")
	void createValidatesName() throws Exception {
		mockMvc.perform(post(CREWS).header(MemberIdHeader.NAME, leaderId)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  새벽한강크루  \"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.name").value("새벽한강크루"));

		for (String invalid : List.of("{\"name\":\"   \"}", "{\"name\":\"" + "가".repeat(21) + "\"}", "{}")) {
			mockMvc.perform(post(CREWS).header(MemberIdHeader.NAME, leaderId)
					.contentType(MediaType.APPLICATION_JSON).content(invalid))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value("크루 이름은 1자 이상 20자 이하로 입력해주세요."));
		}
		// 다른 크루와 이름이 겹쳐도 된다
		createCrew(otherId, "새벽한강크루");
	}

	@Test
	@DisplayName("내 크루 목록은 가입한 크루를 가입한 순서로, 내 역할과 크루원 수와 함께 돌려준다")
	void findMyCrews() throws Exception {
		long first = createCrew(leaderId, "새벽한강크루");
		now = now.plusSeconds(10);
		long second = createCrew(otherId, "판교점심런");
		// 내가 다른 사람 크루에도 가입했다
		String token = createInvite(second, otherId);
		now = now.plusSeconds(10);
		join(leaderId, token).andExpect(status().isCreated());

		mockMvc.perform(get(MY_CREWS).header(MemberIdHeader.NAME, leaderId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].crewId").value(first))
			.andExpect(jsonPath("$.data[0].myRole").value("LEADER"))
			.andExpect(jsonPath("$.data[0].memberCount").value(1))
			.andExpect(jsonPath("$.data[1].crewId").value(second))
			.andExpect(jsonPath("$.data[1].myRole").value("MEMBER"))
			.andExpect(jsonPath("$.data[1].memberCount").value(2));

		mockMvc.perform(get(MY_CREWS).header(MemberIdHeader.NAME, outsiderId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	@DisplayName("크루원이 아닌 회원이 크루 홈을 보면 403, 없는 크루는 404이다")
	void homeForbiddenForOutsider() throws Exception {
		long crewId = createCrew(leaderId, "새벽한강크루");

		mockMvc.perform(get(CREW, crewId).header(MemberIdHeader.NAME, outsiderId))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CREW_NOT_MEMBER"))
			.andExpect(jsonPath("$.message").value("이 크루의 크루원만 이용할 수 있습니다."));
		mockMvc.perform(get(CREW, crewId + 999).header(MemberIdHeader.NAME, leaderId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CREW_NOT_FOUND"));
	}

	@Test
	@DisplayName("크루원 역할은 초대 링크를 만들거나 목록을 볼 수 없고 403이다")
	void memberRoleCannotInvite() throws Exception {
		long crewId = createCrew(leaderId, "새벽한강크루");
		joinWithNewInvite(crewId, leaderId, otherId);

		mockMvc.perform(post(INVITES, crewId).header(MemberIdHeader.NAME, otherId))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CREW_INVITE_FORBIDDEN"))
			.andExpect(jsonPath("$.message").value("크루장과 운영진만 초대할 수 있습니다."));
		mockMvc.perform(get(INVITES, crewId).header(MemberIdHeader.NAME, otherId))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CREW_INVITE_FORBIDDEN"));
		mockMvc.perform(post(INVITES, crewId).header(MemberIdHeader.NAME, outsiderId))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CREW_NOT_MEMBER"));
	}

	@Test
	@DisplayName("초대 링크로 가입하면 201이고 크루원 역할로 크루 홈에 보이며, 홈은 크루장 다음 가입 순서로 정렬된다")
	void joinThenSeenInHome() throws Exception {
		long crewId = createCrew(leaderId, "새벽한강크루");
		String token = createInvite(crewId, leaderId);
		assertThat(token).hasSize(22);

		mockMvc.perform(get(INVITE_BY_TOKEN, token).header(MemberIdHeader.NAME, otherId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.crewId").value(crewId))
			.andExpect(jsonPath("$.data.crewName").value("새벽한강크루"))
			.andExpect(jsonPath("$.data.memberCount").value(1))
			.andExpect(jsonPath("$.data.alreadyMember").value(false));
		mockMvc.perform(get(INVITE_BY_TOKEN, token).header(MemberIdHeader.NAME, leaderId))
			.andExpect(jsonPath("$.data.alreadyMember").value(true));

		now = now.plusSeconds(60);
		join(otherId, token)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.message").value("크루에 가입했습니다."))
			.andExpect(jsonPath("$.data.crewId").value(crewId))
			.andExpect(jsonPath("$.data.crewName").value("새벽한강크루"))
			.andExpect(jsonPath("$.data.role").value("MEMBER"))
			.andExpect(jsonPath("$.data.joinedAt").isNotEmpty());

		mockMvc.perform(get(CREW, crewId).header(MemberIdHeader.NAME, otherId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.myRole").value("MEMBER"))
			.andExpect(jsonPath("$.data.memberCount").value(2))
			.andExpect(jsonPath("$.data.members[0].role").value("LEADER"))
			.andExpect(jsonPath("$.data.members[1].memberId").value(otherId))
			.andExpect(jsonPath("$.data.members[1].role").value("MEMBER"));

		// 자기가 가입에 쓴 링크를 다시 열면 「이미 쓰인 링크」 대신 크루 홈으로 보내도록 200을 준다
		mockMvc.perform(get(INVITE_BY_TOKEN, token).header(MemberIdHeader.NAME, otherId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.alreadyMember").value(true));
	}

	@Test
	@DisplayName("크루원 순서는 크루장, 운영진, 크루원이고 같은 역할은 먼저 가입한 순서다")
	void homeOrdersByRoleThenJoinedAt() throws Exception {
		long crewId = createCrew(leaderId, "새벽한강크루");
		long fourth = join("fourth@crewrun.com", "넷째러너", "010-1000-0004");
		now = now.plusSeconds(10);
		joinWithNewInvite(crewId, leaderId, otherId);
		now = now.plusSeconds(10);
		joinWithNewInvite(crewId, leaderId, outsiderId);
		now = now.plusSeconds(10);
		joinWithNewInvite(crewId, leaderId, fourth);
		// 운영진 역할은 정하는 API가 아직 없어 직접 바꾼다
		jdbcTemplate.update("update member_crew_member set role = 'MANAGER' where member_id = ?", fourth);

		mockMvc.perform(get(CREW, crewId).header(MemberIdHeader.NAME, leaderId))
			.andExpect(jsonPath("$.data.members[0].memberId").value(leaderId))
			.andExpect(jsonPath("$.data.members[1].memberId").value(fourth))
			.andExpect(jsonPath("$.data.members[1].role").value("MANAGER"))
			.andExpect(jsonPath("$.data.members[2].memberId").value(otherId))
			.andExpect(jsonPath("$.data.members[3].memberId").value(outsiderId));
	}

	@Test
	@DisplayName("24시간이 지난 링크는 조회와 가입 모두 410 CREW_INVITE_EXPIRED이다")
	void expiredLinkIsGone() throws Exception {
		long crewId = createCrew(leaderId, "새벽한강크루");
		String token = createInvite(crewId, leaderId);

		now = now.plus(Duration.ofHours(24)).plusSeconds(1);

		mockMvc.perform(get(INVITE_BY_TOKEN, token).header(MemberIdHeader.NAME, otherId))
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.code").value("CREW_INVITE_EXPIRED"))
			.andExpect(jsonPath("$.message").value("초대 링크의 유효 시간(24시간)이 지났습니다. 새 초대 링크를 받아주세요."));
		join(otherId, token)
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.code").value("CREW_INVITE_EXPIRED"));
		// 지난 초대는 목록에서도 빠진다
		mockMvc.perform(get(INVITES, crewId).header(MemberIdHeader.NAME, leaderId))
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	@DisplayName("취소한 링크는 조회와 가입 모두 410 CREW_INVITE_CANCELED이다")
	void canceledLinkIsGone() throws Exception {
		long crewId = createCrew(leaderId, "새벽한강크루");
		String token = createInvite(crewId, leaderId);
		long inviteId = onlyInviteId();

		mockMvc.perform(delete(INVITE, crewId, inviteId).header(MemberIdHeader.NAME, leaderId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("초대를 취소했습니다."));

		mockMvc.perform(get(INVITE_BY_TOKEN, token).header(MemberIdHeader.NAME, otherId))
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.code").value("CREW_INVITE_CANCELED"));
		join(otherId, token)
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.code").value("CREW_INVITE_CANCELED"));
		// 두 번 취소하면 409
		mockMvc.perform(delete(INVITE, crewId, inviteId).header(MemberIdHeader.NAME, leaderId))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CREW_INVITE_CANCELED"))
			.andExpect(jsonPath("$.message").value("이미 취소한 초대입니다."));
	}

	@Test
	@DisplayName("다른 사람이 이미 쓴 링크는 410 CREW_INVITE_ALREADY_USED이고, 쓰인 초대는 취소할 수 없다 (409)")
	void usedLinkIsGone() throws Exception {
		long crewId = createCrew(leaderId, "새벽한강크루");
		String token = createInvite(crewId, leaderId);
		long inviteId = onlyInviteId();
		join(otherId, token).andExpect(status().isCreated());

		join(outsiderId, token)
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.code").value("CREW_INVITE_ALREADY_USED"))
			.andExpect(jsonPath("$.message").value("이미 가입에 쓰인 초대 링크입니다. 새 초대 링크를 받아주세요."));
		mockMvc.perform(get(INVITE_BY_TOKEN, token).header(MemberIdHeader.NAME, outsiderId))
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.code").value("CREW_INVITE_ALREADY_USED"));
		mockMvc.perform(delete(INVITE, crewId, inviteId).header(MemberIdHeader.NAME, leaderId))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CREW_INVITE_ALREADY_USED"));
		assertThat(crewMemberRepository.existsByCrewIdAndMemberId(crewId, outsiderId)).isFalse();
	}

	@Test
	@DisplayName("이미 크루원이면 409 CREW_ALREADY_MEMBER이고 초대를 소모하지 않는다")
	void alreadyMemberDoesNotConsumeInvite() throws Exception {
		long crewId = createCrew(leaderId, "새벽한강크루");
		String token = createInvite(crewId, leaderId);

		join(leaderId, token)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CREW_ALREADY_MEMBER"))
			.andExpect(jsonPath("$.message").value("이미 가입한 크루입니다."));

		Long used = jdbcTemplate.queryForObject(
			"select count(*) from member_crew_invite where used_at is not null", Long.class);
		assertThat(used).isZero();
		// 초대가 남아 있어 다른 사람이 쓸 수 있다
		join(otherId, token).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("초대를 보낸 사람이나 크루장이 아니면 취소할 수 없고 403이다")
	void cancelForbiddenForOthers() throws Exception {
		long crewId = createCrew(leaderId, "새벽한강크루");
		createInvite(crewId, leaderId);
		long inviteId = onlyInviteId();
		joinWithNewInvite(crewId, leaderId, otherId);

		mockMvc.perform(delete(INVITE, crewId, inviteId).header(MemberIdHeader.NAME, otherId))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CREW_INVITE_CANCEL_FORBIDDEN"))
			.andExpect(jsonPath("$.message").value("초대를 보낸 사람이나 크루장만 취소할 수 있습니다."));
		mockMvc.perform(delete(INVITE, crewId, inviteId).header(MemberIdHeader.NAME, outsiderId))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CREW_NOT_MEMBER"));
		mockMvc.perform(delete(INVITE, crewId, inviteId + 999).header(MemberIdHeader.NAME, leaderId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CREW_INVITE_NOT_FOUND"));
	}

	@Test
	@DisplayName("보낸 초대 목록은 크루장에게는 모든 초대, 운영진에게는 자기가 보낸 초대만 보인다")
	void inviteListByRole() throws Exception {
		long crewId = createCrew(leaderId, "새벽한강크루");
		joinWithNewInvite(crewId, leaderId, otherId);
		jdbcTemplate.update("update member_crew_member set role = 'MANAGER' where member_id = ?", otherId);
		createInvite(crewId, leaderId);
		createInvite(crewId, otherId);

		mockMvc.perform(get(INVITES, crewId).header(MemberIdHeader.NAME, leaderId))
			.andExpect(status().isOk())
			// 처음 가입에 쓴 초대는 목록에서 빠진다
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].inviteId").isNumber())
			.andExpect(jsonPath("$.data[0].inviteToken").doesNotExist());
		mockMvc.perform(get(INVITES, crewId).header(MemberIdHeader.NAME, otherId))
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].createdBy.memberId").value(otherId))
			.andExpect(jsonPath("$.data[0].createdBy.nickname").value("주말10K"));
	}

	@Test
	@DisplayName("DB에는 토큰 원문이 없고 SHA-256 값(64자)만 있으며, 없는 토큰은 404이다")
	void storesOnlyTokenHash() throws Exception {
		long crewId = createCrew(leaderId, "새벽한강크루");
		String token = createInvite(crewId, leaderId);

		String hash = jdbcTemplate.queryForObject("select token_hash from member_crew_invite", String.class);
		assertThat(hash).hasSize(64).isNotEqualTo(token);
		byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
		assertThat(hash.trim()).isEqualTo(HexFormat.of().formatHex(digest));
		Long rawMatches = jdbcTemplate.queryForObject(
			"select count(*) from member_crew_invite where token_hash = ?", Long.class, token);
		assertThat(rawMatches).isZero();

		mockMvc.perform(get(INVITE_BY_TOKEN, "없는토큰").header(MemberIdHeader.NAME, otherId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CREW_INVITE_NOT_FOUND"));
		join(otherId, "없는토큰")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CREW_INVITE_NOT_FOUND"));
	}

	@Test
	@DisplayName("회원 ID 헤더가 없으면 401이다")
	void loginRequired() throws Exception {
		mockMvc.perform(get(MY_CREWS))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
	}

	private long createCrew(long memberId, String name) throws Exception {
		String response = mockMvc.perform(post(CREWS).header(MemberIdHeader.NAME, memberId)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return ((Number)JsonPath.read(response, "$.data.crewId")).longValue();
	}

	private String createInvite(long crewId, long memberId) throws Exception {
		String response = mockMvc.perform(post(INVITES, crewId).header(MemberIdHeader.NAME, memberId))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.inviteToken").isString())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return JsonPath.read(response, "$.data.inviteToken");
	}

	// 초대를 만들어 그 링크로 가입시킨다
	private void joinWithNewInvite(long crewId, long senderId, long joinerId) throws Exception {
		if (senderId == joinerId) {
			return;
		}
		join(joinerId, createInvite(crewId, senderId)).andExpect(status().isCreated());
	}

	private ResultActions join(long memberId, String token) throws Exception {
		return mockMvc.perform(post(JOIN, token).header(MemberIdHeader.NAME, memberId));
	}

	private long onlyInviteId() {
		Long id = jdbcTemplate.queryForObject("select max(invite_id) from member_crew_invite", Long.class);
		return id;
	}

	private long join(String email, String nickname, String phone) {
		return memberFacade.join(new MemberJoinRequestDto(
			email, "password1234", "김러너", nickname, phone, LocalDate.of(1995, 4, 12), null,
			List.of(TermsType.SERVICE, TermsType.PRIVACY)
		)).getId();
	}
}
