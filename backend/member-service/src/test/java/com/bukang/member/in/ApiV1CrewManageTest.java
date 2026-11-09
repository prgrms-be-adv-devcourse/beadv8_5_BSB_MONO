package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.common.shared.member.web.MemberIdHeader;
import com.bukang.member.app.MemberFacade;
import com.bukang.member.crew.app.CrewFacade;
import com.bukang.member.crew.out.CrewInviteRepository;
import com.bukang.member.crew.out.CrewMemberRepository;
import com.bukang.member.crew.out.CrewRepository;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.RefreshTokenRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.cash.CashBalancePort;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.out.order.ActiveTicketPort;
import com.bukang.member.support.MemberFixture;

// 크루 관리 (BE-032): 탈퇴, 내보내기, 크루장 넘기기, 없애기, 이름 바꾸기. X-Member-Id 헤더는 테스트에서 직접 넣는다
// 크루 상태를 바꾸는 요청은 크루 행을 잠가 차례로 처리되는지 동시 요청으로 확인한다
// 회원 탈퇴와 겹치는 경우를 보려고 예치금·주문 포트와 Refresh Token 저장소를 목으로 바꾼다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InMemoryIdentityVerificationStore.Config.class)
class ApiV1CrewManageTest {
	private static final String MY_CREWS = "/api/v1/member/crews/me";
	private static final String CREW = "/api/v1/member/crews/{crewId}";
	private static final String LEAVE = "/api/v1/member/crews/{crewId}/members/me";
	private static final String CREW_MEMBER = "/api/v1/member/crews/{crewId}/members/{memberId}";
	private static final String LEADER = "/api/v1/member/crews/{crewId}/leader";
	private static final String INVITE_BY_TOKEN = "/api/v1/member/crews/invites/{inviteToken}";
	private static final String JOIN = "/api/v1/member/crews/invites/{inviteToken}/join";
	private static final String ME = "/api/v1/member/members/me";

	@Autowired
	private InMemoryIdentityVerificationStore identityStore;
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

	@MockitoBean
	private CashBalancePort cashBalancePort;
	@MockitoBean
	private ActiveTicketPort activeTicketPort;
	@MockitoBean
	private RefreshTokenRepository refreshTokenRepository;
	@MockitoBean
	private Clock clock;

	private Instant now;
	private long leaderId;
	private long managerId;
	private long memberId;
	private long outsiderId;
	private long crewId;

	// 크루장, 운영진, 크루원이 있는 크루 하나와 크루에 없는 회원 한 명
	@BeforeEach
	void setUp() {
		now = Instant.now();
		given(clock.instant()).willAnswer(invocation -> now);
		leaderId = joinMember();
		managerId = joinMember();
		memberId = joinMember();
		outsiderId = joinMember();
		crewId = crewFacade.createCrew(leaderId, "새벽한강크루").crewId();
		joinCrew(crewId, leaderId, managerId);
		joinCrew(crewId, leaderId, memberId);
		jdbcTemplate.update("update member_crew_member set role = 'MANAGER' where member_id = ?", managerId);
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

	// ---- 크루 탈퇴 ----

	@Test
	@DisplayName("크루원이 탈퇴하면 200이고 크루원 행이 지워져 크루 홈을 볼 수 없다")
	void memberLeaves() throws Exception {
		leave(memberId, crewId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("크루를 탈퇴했습니다."));

		assertThat(crewMemberRepository.existsByCrewIdAndMemberId(crewId, memberId)).isFalse();
		assertThat(crewMemberRepository.countByCrewId(crewId)).isEqualTo(2);
		mockMvc.perform(get(CREW, crewId).header(MemberIdHeader.NAME, memberId))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CREW_NOT_MEMBER"));
		// 운영진도 스스로 떠날 수 있다
		leave(managerId, crewId).andExpect(status().isOk());
	}

	@Test
	@DisplayName("크루장이 탈퇴하면 409 CREW_LEADER_CANNOT_LEAVE이고 크루장으로 남는다. 혼자 남아도 같다")
	void leaderCannotLeave() throws Exception {
		leave(leaderId, crewId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CREW_LEADER_CANNOT_LEAVE"))
			.andExpect(jsonPath("$.message")
				.value("크루장은 크루장을 넘긴 뒤 탈퇴할 수 있습니다. 다른 크루원이 없으면 크루를 없애주세요."));
		assertThat(roleOf(crewId, leaderId)).isEqualTo("LEADER");

		long soloCrewId = crewFacade.createCrew(outsiderId, "혼자크루").crewId();
		leave(outsiderId, soloCrewId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CREW_LEADER_CANNOT_LEAVE"));
	}

	@Test
	@DisplayName("크루원이 아닌 회원이 탈퇴하면 403 CREW_NOT_MEMBER, 없는 크루는 404 CREW_NOT_FOUND이다")
	void leaveByOutsiderOrMissingCrew() throws Exception {
		leave(outsiderId, crewId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CREW_NOT_MEMBER"));
		leave(memberId, crewId + 999)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CREW_NOT_FOUND"));
	}

	@Test
	@DisplayName("떠났다가 다시 가입하면 가입 시각이 새로 정해진다 (가입 기간 규칙을 피할 수 없다)")
	void rejoinGetsNewJoinedAt() throws Exception {
		Timestamp firstJoinedAt = joinedAtOf(crewId, memberId);
		leave(memberId, crewId).andExpect(status().isOk());

		now = now.plusSeconds(3600);
		joinCrew(crewId, leaderId, memberId);

		assertThat(joinedAtOf(crewId, memberId)).isAfter(firstJoinedAt);
	}

	// ---- 크루원 내보내기 ----

	@Test
	@DisplayName("크루장이 크루원과 운영진을 내보내면 200이고 크루원 행이 지워진다")
	void leaderRemovesMemberAndManager() throws Exception {
		remove(leaderId, crewId, memberId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("크루원을 내보냈습니다."));
		remove(leaderId, crewId, managerId).andExpect(status().isOk());

		assertThat(crewMemberRepository.existsByCrewIdAndMemberId(crewId, memberId)).isFalse();
		assertThat(crewMemberRepository.existsByCrewIdAndMemberId(crewId, managerId)).isFalse();
		assertThat(crewMemberRepository.countByCrewId(crewId)).isEqualTo(1);
	}

	@Test
	@DisplayName("내보낸 크루원은 내보내기 전에 만든 링크로 다시 가입할 수 없고 410 CREW_INVITE_REMOVED_MEMBER이다. 다른 사람은 그 링크로 가입한다")
	void removedMemberCannotRejoinWithOldLink() throws Exception {
		String oldToken = crewFacade.createInvite(leaderId, crewId).inviteToken();
		now = now.plusSeconds(60);
		remove(leaderId, crewId, memberId).andExpect(status().isOk());

		mockMvc.perform(get(INVITE_BY_TOKEN, oldToken).header(MemberIdHeader.NAME, memberId))
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.code").value("CREW_INVITE_REMOVED_MEMBER"));
		mockMvc.perform(post(JOIN, oldToken).header(MemberIdHeader.NAME, memberId))
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.code").value("CREW_INVITE_REMOVED_MEMBER"));
		assertThat(crewMemberRepository.existsByCrewIdAndMemberId(crewId, memberId)).isFalse();

		mockMvc.perform(post(JOIN, oldToken).header(MemberIdHeader.NAME, outsiderId))
			.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("내보낸 뒤 크루장이 새로 만든 링크로는 다시 가입할 수 있다")
	void removedMemberRejoinsWithNewLink() throws Exception {
		remove(leaderId, crewId, memberId).andExpect(status().isOk());
		now = now.plusSeconds(60);
		String newToken = crewFacade.createInvite(leaderId, crewId).inviteToken();

		mockMvc.perform(post(JOIN, newToken).header(MemberIdHeader.NAME, memberId))
			.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("스스로 탈퇴한 크루원은 같은 링크로 다시 가입할 수 있다")
	void leftMemberRejoinsWithSameLink() throws Exception {
		String token = crewFacade.createInvite(leaderId, crewId).inviteToken();
		now = now.plusSeconds(60);
		leave(memberId, crewId).andExpect(status().isOk());

		mockMvc.perform(post(JOIN, token).header(MemberIdHeader.NAME, memberId))
			.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("운영진과 크루원은 내보낼 수 없고 403 CREW_LEADER_ONLY이며, 크루원이 아니면 403 CREW_NOT_MEMBER이다")
	void onlyLeaderRemoves() throws Exception {
		remove(managerId, crewId, memberId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CREW_LEADER_ONLY"))
			.andExpect(jsonPath("$.message").value("크루장만 할 수 있습니다."));
		remove(memberId, crewId, managerId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CREW_LEADER_ONLY"));
		remove(outsiderId, crewId, memberId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CREW_NOT_MEMBER"));

		assertThat(crewMemberRepository.countByCrewId(crewId)).isEqualTo(3);
	}

	@Test
	@DisplayName("크루장이 자기 자신을 내보내면 409 CREW_CANNOT_REMOVE_LEADER, 크루원이 아닌 회원은 404 CREW_MEMBER_NOT_FOUND이다")
	void removeLeaderOrNonMember() throws Exception {
		remove(leaderId, crewId, leaderId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CREW_CANNOT_REMOVE_LEADER"))
			.andExpect(jsonPath("$.message").value("크루장은 내보낼 수 없습니다."));
		remove(leaderId, crewId, outsiderId)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CREW_MEMBER_NOT_FOUND"))
			.andExpect(jsonPath("$.message").value("이 크루의 크루원이 아닙니다."));
		assertThat(roleOf(crewId, leaderId)).isEqualTo("LEADER");
	}

	@Test
	@DisplayName("크루장이 자기 크루 ID로 다른 크루의 크루원을 내보내려 하면 404이고, 다른 크루 ID로는 403이다. 다른 크루는 그대로다")
	void removeScopedToCrew() throws Exception {
		long otherCrewId = crewFacade.createCrew(outsiderId, "판교점심런").crewId();
		long otherMemberId = joinMember();
		joinCrew(otherCrewId, outsiderId, otherMemberId);

		remove(leaderId, crewId, otherMemberId)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CREW_MEMBER_NOT_FOUND"));
		remove(leaderId, otherCrewId, otherMemberId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CREW_NOT_MEMBER"));

		assertThat(crewMemberRepository.existsByCrewIdAndMemberId(otherCrewId, otherMemberId)).isTrue();
	}

	@Test
	@DisplayName("같은 크루원을 동시에 두 번 내보내면 한 번만 200이고 다른 하나는 404 CREW_MEMBER_NOT_FOUND이다")
	void concurrentRemoveSameMember() throws Exception {
		List<Integer> statuses = atOnce(
			() -> statusOf(remove(leaderId, crewId, memberId)),
			() -> statusOf(remove(leaderId, crewId, memberId)));

		assertThat(statuses).containsExactlyInAnyOrder(200, 404);
		assertThat(crewMemberRepository.countByCrewId(crewId)).isEqualTo(2);
	}

	@Test
	@DisplayName("내보내기와 그 크루원의 회원 탈퇴가 동시에 와도 500이 아니고, 내보내기는 200이나 404이다")
	void concurrentRemoveAndMemberWithdraw() throws Exception {
		List<Integer> statuses = atOnce(
			() -> statusOf(remove(leaderId, crewId, memberId)),
			() -> mockMvc.perform(delete(ME).header(MemberIdHeader.NAME, memberId))
				.andReturn().getResponse().getStatus());

		// 회원 탈퇴는 크루 행을 잠그지 않아 같은 크루원 행을 먼저 지울 수 있다
		assertThat(statuses.get(0)).isIn(200, 404);
		assertThat(statuses.get(1)).isEqualTo(200);
		assertThat(crewMemberRepository.existsByCrewIdAndMemberId(crewId, memberId)).isFalse();
	}

	// ---- 크루장 넘기기 ----

	@Test
	@DisplayName("크루장을 넘기면 200이고, 넘긴 사람은 크루원, 넘겨받은 사람은 크루장이며 가입 시각은 그대로다")
	void transferLeader() throws Exception {
		Timestamp joinedAt = joinedAtOf(crewId, memberId);
		now = now.plusSeconds(3600);

		transfer(leaderId, crewId, memberId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("크루장을 넘겼습니다."))
			.andExpect(jsonPath("$.data.crewId").value(crewId))
			.andExpect(jsonPath("$.data.myRole").value("MEMBER"))
			.andExpect(jsonPath("$.data.members[0].memberId").value(memberId))
			.andExpect(jsonPath("$.data.members[0].role").value("LEADER"));

		assertThat(roleOf(crewId, leaderId)).isEqualTo("MEMBER");
		assertThat(roleOf(crewId, memberId)).isEqualTo("LEADER");
		assertThat(joinedAtOf(crewId, memberId)).isEqualTo(joinedAt);
		// 넘긴 뒤에는 떠날 수 있다
		leave(leaderId, crewId).andExpect(status().isOk());
	}

	@Test
	@DisplayName("크루장 넘기기는 크루장만 하고(403), 자기 자신은 409, 크루원이 아닌 회원은 404, 대상이 없으면 400이다")
	void transferRejects() throws Exception {
		transfer(managerId, crewId, memberId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CREW_LEADER_ONLY"));
		transfer(outsiderId, crewId, memberId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CREW_NOT_MEMBER"));
		transfer(leaderId, crewId, leaderId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CREW_ALREADY_LEADER"))
			.andExpect(jsonPath("$.message").value("이미 크루장입니다."));
		transfer(leaderId, crewId, outsiderId)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CREW_MEMBER_NOT_FOUND"));
		mockMvc.perform(put(LEADER, crewId).header(MemberIdHeader.NAME, leaderId)
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		assertThat(roleOf(crewId, leaderId)).isEqualTo("LEADER");
		assertThat(countLeaders(crewId)).isEqualTo(1);
	}

	@Test
	@DisplayName("크루장 넘기기와 넘겨받을 사람의 탈퇴가 동시에 오면 차례로 처리되어 크루장은 늘 한 명이다")
	void concurrentTransferAndLeave() throws Exception {
		List<Integer> statuses = atOnce(
			() -> statusOf(transfer(leaderId, crewId, memberId)),
			() -> statusOf(leave(memberId, crewId)));

		// 넘기기가 먼저면 넘겨받은 사람은 크루장이라 떠날 수 없고, 탈퇴가 먼저면 넘길 대상이 없다
		assertThat(statuses).isIn(List.of(200, 409), List.of(404, 200));
		assertThat(countLeaders(crewId)).isEqualTo(1);
	}

	@Test
	@DisplayName("크루장 넘기기와 넘겨받을 사람의 회원 탈퇴가 동시에 오면 크루장 없는 크루가 생기지 않는다")
	void concurrentTransferAndMemberWithdraw() throws Exception {
		List<Integer> statuses = atOnce(
			() -> statusOf(transfer(leaderId, crewId, memberId)),
			() -> mockMvc.perform(delete(ME).header(MemberIdHeader.NAME, memberId))
				.andReturn().getResponse().getStatus());

		// 넘기기가 먼저면 크루장이라 회원 탈퇴가 막히고, 회원 탈퇴가 먼저면 넘길 대상이 없다
		assertThat(statuses).isIn(List.of(200, 409), List.of(404, 200));
		assertThat(countLeaders(crewId)).isEqualTo(1);
		Long leaderAlive = jdbcTemplate.queryForObject(
			"select count(*) from member_crew_member c join member_member m on m.member_id = c.member_id"
				+ " where c.crew_id = ? and c.role = 'LEADER' and m.status <> 'WITHDRAWN'", Long.class, crewId);
		assertThat(leaderAlive).isEqualTo(1);
	}

	// ---- 크루 없애기 ----

	@Test
	@DisplayName("크루장 혼자일 때 없애면 200이고, 크루 행은 남아 상태만 바뀌며 크루원·초대 행은 지워진다")
	void disbandWhenAlone() throws Exception {
		remove(leaderId, crewId, managerId).andExpect(status().isOk());
		remove(leaderId, crewId, memberId).andExpect(status().isOk());
		String unusedToken = crewFacade.createInvite(leaderId, crewId).inviteToken();

		disband(leaderId, crewId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("크루를 없앴습니다."));

		String crewStatus = jdbcTemplate.queryForObject(
			"select status from member_crew where crew_id = ?", String.class, crewId);
		assertThat(crewStatus).isEqualTo("DISBANDED");
		Long disbandedAtSet = jdbcTemplate.queryForObject(
			"select count(*) from member_crew where crew_id = ? and disbanded_at is not null", Long.class, crewId);
		assertThat(disbandedAtSet).isEqualTo(1);
		assertThat(crewMemberRepository.countByCrewId(crewId)).isZero();
		Long invites = jdbcTemplate.queryForObject(
			"select count(*) from member_crew_invite where crew_id = ?", Long.class, crewId);
		assertThat(invites).isZero();

		mockMvc.perform(get(CREW, crewId).header(MemberIdHeader.NAME, leaderId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CREW_NOT_FOUND"));
		mockMvc.perform(get(MY_CREWS).header(MemberIdHeader.NAME, leaderId))
			.andExpect(jsonPath("$.data.length()").value(0));
		mockMvc.perform(get(INVITE_BY_TOKEN, unusedToken).header(MemberIdHeader.NAME, outsiderId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CREW_INVITE_NOT_FOUND"));
	}

	@Test
	@DisplayName("다른 크루원이 있으면 409 CREW_HAS_OTHER_MEMBERS이고 아무것도 바뀌지 않는다")
	void disbandWithOtherMembers() throws Exception {
		crewFacade.createInvite(leaderId, crewId);

		disband(leaderId, crewId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CREW_HAS_OTHER_MEMBERS"))
			.andExpect(jsonPath("$.message")
				.value("다른 크루원이 있어 크루를 없앨 수 없습니다. 크루장을 넘긴 뒤 탈퇴해주세요."));

		assertThat(crewRepository.findActiveById(crewId)).isPresent();
		assertThat(crewMemberRepository.countByCrewId(crewId)).isEqualTo(3);
		Long invites = jdbcTemplate.queryForObject(
			"select count(*) from member_crew_invite where crew_id = ?", Long.class, crewId);
		assertThat(invites).isEqualTo(3);
	}

	@Test
	@DisplayName("크루장이 아니면 없앨 수 없고 403 CREW_LEADER_ONLY, 크루원이 아니면 403 CREW_NOT_MEMBER이다")
	void disbandOnlyLeader() throws Exception {
		disband(managerId, crewId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CREW_LEADER_ONLY"));
		disband(outsiderId, crewId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CREW_NOT_MEMBER"));
		assertThat(crewRepository.findActiveById(crewId)).isPresent();
	}

	@Test
	@DisplayName("없앤 크루에는 다섯 관리 API 모두 404 CREW_NOT_FOUND로 답한다")
	void disbandedCrewIsNotFound() throws Exception {
		long soloCrewId = crewFacade.createCrew(outsiderId, "혼자크루").crewId();
		disband(outsiderId, soloCrewId).andExpect(status().isOk());

		for (ResultActions result : List.of(
			leave(outsiderId, soloCrewId),
			remove(outsiderId, soloCrewId, memberId),
			transfer(outsiderId, soloCrewId, memberId),
			disband(outsiderId, soloCrewId),
			rename(outsiderId, soloCrewId, "{\"name\":\"다시크루\"}"))) {
			result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CREW_NOT_FOUND"));
		}
	}

	@Test
	@DisplayName("크루 없애기와 초대 가입이 동시에 오면 차례로 처리되어, 없앤 크루에 크루원이 남지 않는다")
	void concurrentDisbandAndJoin() throws Exception {
		long soloCrewId = crewFacade.createCrew(outsiderId, "혼자크루").crewId();
		String token = crewFacade.createInvite(outsiderId, soloCrewId).inviteToken();
		long joinerId = joinMember();

		List<Integer> statuses = atOnce(
			() -> statusOf(disband(outsiderId, soloCrewId)),
			() -> mockMvc.perform(post(JOIN, token).header(MemberIdHeader.NAME, joinerId))
				.andReturn().getResponse().getStatus());

		// 없애기가 먼저면 링크가 사라져 404, 가입이 먼저면 다른 크루원이 생겨 없애기가 409
		assertThat(statuses).isIn(List.of(200, 404), List.of(409, 201));
		String crewStatus = jdbcTemplate.queryForObject(
			"select status from member_crew where crew_id = ?", String.class, soloCrewId);
		long members = crewMemberRepository.countByCrewId(soloCrewId);
		if ("DISBANDED".equals(crewStatus)) {
			assertThat(members).isZero();
		} else {
			assertThat(members).isEqualTo(2);
		}
	}

	// ---- 크루 이름 바꾸기 ----

	@Test
	@DisplayName("크루장이 이름을 바꾸면 200이고 앞뒤 공백을 뺀 이름이 크루 홈 형식으로 돌아온다")
	void renameByLeader() throws Exception {
		rename(leaderId, crewId, "{\"name\":\"  새벽한강러닝크루  \"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("크루 이름을 바꿨습니다."))
			.andExpect(jsonPath("$.data.crewId").value(crewId))
			.andExpect(jsonPath("$.data.name").value("새벽한강러닝크루"))
			.andExpect(jsonPath("$.data.myRole").value("LEADER"))
			.andExpect(jsonPath("$.data.memberCount").value(3));

		assertThat(crewRepository.findActiveById(crewId).orElseThrow().getName()).isEqualTo("새벽한강러닝크루");
		// 다른 크루와 이름이 겹쳐도 된다
		long otherCrewId = crewFacade.createCrew(outsiderId, "판교점심런").crewId();
		rename(outsiderId, otherCrewId, "{\"name\":\"새벽한강러닝크루\"}").andExpect(status().isOk());
	}

	@Test
	@DisplayName("이름이 비었거나 20자를 넘거나 없으면 400 INVALID_REQUEST이고 이름은 그대로다")
	void renameValidatesName() throws Exception {
		for (String invalid : List.of("{\"name\":\"   \"}", "{\"name\":\"" + "가".repeat(21) + "\"}", "{}")) {
			rename(leaderId, crewId, invalid)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value("크루 이름은 1자 이상 20자 이하로 입력해주세요."));
		}
		assertThat(crewRepository.findActiveById(crewId).orElseThrow().getName()).isEqualTo("새벽한강크루");
	}

	@Test
	@DisplayName("운영진·크루원은 이름을 바꿀 수 없고 403 CREW_LEADER_ONLY, 크루원이 아니면 403 CREW_NOT_MEMBER이다")
	void renameOnlyLeader() throws Exception {
		rename(managerId, crewId, "{\"name\":\"바꾼이름\"}")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CREW_LEADER_ONLY"));
		rename(memberId, crewId, "{\"name\":\"바꾼이름\"}")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CREW_LEADER_ONLY"));
		rename(outsiderId, crewId, "{\"name\":\"바꾼이름\"}")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CREW_NOT_MEMBER"));
		assertThat(crewRepository.findActiveById(crewId).orElseThrow().getName()).isEqualTo("새벽한강크루");
	}

	@Test
	@DisplayName("회원 ID 헤더가 없으면 다섯 관리 API 모두 401이다")
	void loginRequired() throws Exception {
		for (ResultActions result : List.of(
			mockMvc.perform(delete(LEAVE, crewId)),
			mockMvc.perform(delete(CREW_MEMBER, crewId, memberId)),
			mockMvc.perform(put(LEADER, crewId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"memberId\":" + memberId + "}")),
			mockMvc.perform(delete(CREW, crewId)),
			mockMvc.perform(patch(CREW, crewId).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"a\"}")))) {
			result.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
		}
	}

	private long joinMember() {
		return memberFacade.join(MemberFixture.joinRequest().build(identityStore)).getId();
	}

	// 초대를 만들어 그 링크로 가입시킨다
	private void joinCrew(long targetCrewId, long senderId, long joinerId) {
		crewFacade.join(joinerId, crewFacade.createInvite(senderId, targetCrewId).inviteToken());
	}

	private ResultActions leave(long requesterId, long targetCrewId) throws Exception {
		return mockMvc.perform(delete(LEAVE, targetCrewId).header(MemberIdHeader.NAME, requesterId));
	}

	private ResultActions remove(long requesterId, long targetCrewId, long targetMemberId) throws Exception {
		return mockMvc.perform(delete(CREW_MEMBER, targetCrewId, targetMemberId)
			.header(MemberIdHeader.NAME, requesterId));
	}

	private ResultActions transfer(long requesterId, long targetCrewId, long targetMemberId) throws Exception {
		return mockMvc.perform(put(LEADER, targetCrewId).header(MemberIdHeader.NAME, requesterId)
			.contentType(MediaType.APPLICATION_JSON).content("{\"memberId\":" + targetMemberId + "}"));
	}

	private ResultActions disband(long requesterId, long targetCrewId) throws Exception {
		return mockMvc.perform(delete(CREW, targetCrewId).header(MemberIdHeader.NAME, requesterId));
	}

	private ResultActions rename(long requesterId, long targetCrewId, String body) throws Exception {
		return mockMvc.perform(patch(CREW, targetCrewId).header(MemberIdHeader.NAME, requesterId)
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static int statusOf(ResultActions result) {
		return result.andReturn().getResponse().getStatus();
	}

	// 두 요청을 동시에 출발시키고, 넘긴 순서대로 각 응답 상태 코드를 돌려준다
	private List<Integer> atOnce(Callable<Integer> first, Callable<Integer> second) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for (Callable<Integer> request : List.of(first, second)) {
				results.add(executor.submit(() -> {
					start.await();
					return request.call();
				}));
			}
			start.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> result : results) {
				statuses.add(result.get(10, TimeUnit.SECONDS));
			}
			return statuses;
		} finally {
			executor.shutdownNow();
		}
	}

	private String roleOf(long targetCrewId, long targetMemberId) {
		return jdbcTemplate.queryForObject("select role from member_crew_member where crew_id = ? and member_id = ?",
			String.class, targetCrewId, targetMemberId);
	}

	private Timestamp joinedAtOf(long targetCrewId, long targetMemberId) {
		return jdbcTemplate.queryForObject(
			"select joined_at from member_crew_member where crew_id = ? and member_id = ?",
			Timestamp.class, targetCrewId, targetMemberId);
	}

	private long countLeaders(long targetCrewId) {
		Long count = jdbcTemplate.queryForObject(
			"select count(*) from member_crew_member where crew_id = ? and role = 'LEADER'", Long.class, targetCrewId);
		return count;
	}
}
