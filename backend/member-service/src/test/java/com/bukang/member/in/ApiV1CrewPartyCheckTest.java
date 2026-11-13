package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.common.shared.member.web.MemberIdHeader;
import com.bukang.member.app.MemberFacade;
import com.bukang.member.crew.app.CrewFacade;
import com.bukang.member.crew.domain.exception.CrewPartyCheckFailedException;
import com.bukang.member.crew.out.CrewInviteRepository;
import com.bukang.member.crew.out.CrewMemberRepository;
import com.bukang.member.crew.out.CrewRemovalRepository;
import com.bukang.member.crew.out.CrewRepository;
import com.bukang.member.out.MemberOutboxEventRepository;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.RefreshTokenRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.cash.CashBalancePort;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.out.order.ActiveTicketPort;
import com.bukang.member.out.order.CrewPartyStatusPort;
import com.bukang.member.out.order.CrewPartyStatusPort.PartyStatus;
import com.bukang.member.support.MemberFixture;

// 크루 나가기·내보내기 전 파티 확인 (BUK-102). 주문 서비스 답은 목으로 정하고,
// 막히면 크루원 행·내보내기 기록·아웃박스가 모두 그대로인지 본다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InMemoryIdentityVerificationStore.Config.class)
class ApiV1CrewPartyCheckTest {
	private static final String LEAVE = "/api/v1/member/crews/{crewId}/members/me";
	private static final String CREW_MEMBER = "/api/v1/member/crews/{crewId}/members/{memberId}";

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
	private CrewRemovalRepository crewRemovalRepository;
	@Autowired
	private MemberOutboxEventRepository memberOutboxEventRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	private CrewPartyStatusPort partyStatusPort;
	@MockitoBean
	private CashBalancePort cashBalancePort;
	@MockitoBean
	private ActiveTicketPort activeTicketPort;
	@MockitoBean
	private RefreshTokenRepository refreshTokenRepository;

	private long leaderId;
	private long managerId;
	private long memberId;
	private long outsiderId;
	private long crewId;
	private long outboxBefore;

	@BeforeEach
	void setUp() {
		leaderId = joinMember();
		managerId = joinMember();
		memberId = joinMember();
		outsiderId = joinMember();
		crewId = crewFacade.createCrew(leaderId, "새벽한강크루").crewId();
		crewFacade.join(managerId, crewFacade.createInvite(leaderId, crewId).inviteToken());
		crewFacade.join(memberId, crewFacade.createInvite(leaderId, crewId).inviteToken());
		jdbcTemplate.update("update member_crew_member set role = 'MANAGER' where member_id = ?", managerId);
		outboxBefore = memberOutboxEventRepository.count();
	}

	@AfterEach
	void tearDown() {
		crewInviteRepository.deleteAll();
		crewRemovalRepository.deleteAll();
		crewMemberRepository.deleteAll();
		crewRepository.deleteAll();
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	// ---- 나가기 ----

	@Test
	@DisplayName("canLeave가 false면 409 CREW_LEAVE_PARTY_BLOCKED이고 크루원 행과 아웃박스가 그대로다")
	void leaveBlocked() throws Exception {
		given(partyStatusPort.findStatus(crewId, memberId)).willReturn(new PartyStatus(false, true));

		leave(memberId, crewId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CREW_LEAVE_PARTY_BLOCKED"))
			.andExpect(jsonPath("$.message")
				.value("참여 중인 파티가 있어 크루를 나갈 수 없습니다. 파티에서 나온 뒤 다시 시도해주세요."));

		assertThat(crewMemberRepository.existsByCrewIdAndMemberId(crewId, memberId)).isTrue();
		assertThat(memberOutboxEventRepository.count()).isEqualTo(outboxBefore);
	}

	@Test
	@DisplayName("canLeave가 true면 나가고, 주문 서비스에는 URL의 크루 ID와 요청한 본인의 회원 ID로 묻는다")
	void leaveAllowed() throws Exception {
		given(partyStatusPort.findStatus(crewId, memberId)).willReturn(new PartyStatus(true, false));

		leave(memberId, crewId).andExpect(status().isOk());

		verify(partyStatusPort).findStatus(crewId, memberId);
		assertThat(crewMemberRepository.existsByCrewIdAndMemberId(crewId, memberId)).isFalse();
		assertThat(memberOutboxEventRepository.count()).isEqualTo(outboxBefore + 1);
	}

	@Test
	@DisplayName("주문 서비스 확인에 실패하면 503 CREW_PARTY_CHECK_FAILED이고 크루원 행과 아웃박스가 그대로다")
	void leaveCheckFailed() throws Exception {
		given(partyStatusPort.findStatus(crewId, memberId)).willThrow(new CrewPartyCheckFailedException("x"));

		leave(memberId, crewId)
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("CREW_PARTY_CHECK_FAILED"));

		assertThat(crewMemberRepository.existsByCrewIdAndMemberId(crewId, memberId)).isTrue();
		assertThat(memberOutboxEventRepository.count()).isEqualTo(outboxBefore);
	}

	@Test
	@DisplayName("크루원이 아닌 회원·크루장의 나가기는 주문 서비스를 부르지 않고 기존 오류(403·409)를 준다")
	void leaveByNonMemberOrLeaderSkipsCheck() throws Exception {
		leave(outsiderId, crewId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CREW_NOT_MEMBER"));
		leave(leaderId, crewId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CREW_LEADER_CANNOT_LEAVE"));

		verify(partyStatusPort, never()).findStatus(anyLong(), anyLong());
	}

	// ---- 내보내기 ----

	@Test
	@DisplayName("canKick이 false면 409 CREW_KICK_PARTY_BLOCKED이고 크루원 행·내보내기 기록·아웃박스가 그대로다")
	void kickBlocked() throws Exception {
		given(partyStatusPort.findStatus(crewId, memberId)).willReturn(new PartyStatus(false, false));

		remove(leaderId, crewId, memberId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CREW_KICK_PARTY_BLOCKED"))
			.andExpect(jsonPath("$.message")
				.value("결제가 진행 중이거나 끝난 파티에 들어 있는 크루원은 내보낼 수 없습니다. 파티가 끝난 뒤 다시 시도해주세요."));

		assertThat(crewMemberRepository.existsByCrewIdAndMemberId(crewId, memberId)).isTrue();
		assertThat(crewRemovalRepository.count()).isZero();
		assertThat(memberOutboxEventRepository.count()).isEqualTo(outboxBefore);
	}

	@Test
	@DisplayName("내보내기는 canKick만 따른다. 모집 중 파티(canLeave=false, canKick=true)는 내보낼 수 있고, 묻는 대상은 내보낼 크루원이다")
	void kickAllowedWhileRecruiting() throws Exception {
		given(partyStatusPort.findStatus(crewId, memberId)).willReturn(new PartyStatus(false, true));

		remove(leaderId, crewId, memberId).andExpect(status().isOk());

		verify(partyStatusPort).findStatus(crewId, memberId);
		assertThat(crewMemberRepository.existsByCrewIdAndMemberId(crewId, memberId)).isFalse();
		assertThat(memberOutboxEventRepository.count()).isEqualTo(outboxBefore + 1);
	}

	@Test
	@DisplayName("주문 서비스 확인에 실패하면 내보내기도 503 CREW_PARTY_CHECK_FAILED이고 아무것도 바뀌지 않는다")
	void kickCheckFailed() throws Exception {
		given(partyStatusPort.findStatus(crewId, memberId)).willThrow(new CrewPartyCheckFailedException("x"));

		remove(leaderId, crewId, memberId)
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("CREW_PARTY_CHECK_FAILED"));

		assertThat(crewMemberRepository.existsByCrewIdAndMemberId(crewId, memberId)).isTrue();
		assertThat(crewRemovalRepository.count()).isZero();
		assertThat(memberOutboxEventRepository.count()).isEqualTo(outboxBefore);
	}

	@Test
	@DisplayName("크루장이 아닌 사람의 내보내기는 주문 서비스를 부르지 않는다. 403으로 끝나 다른 크루원의 파티 정보가 새지 않는다")
	void kickByNonLeaderSkipsCheck() throws Exception {
		remove(managerId, crewId, memberId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CREW_LEADER_ONLY"));
		remove(outsiderId, crewId, memberId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CREW_NOT_MEMBER"));

		verify(partyStatusPort, never()).findStatus(anyLong(), anyLong());
	}

	@Test
	@DisplayName("다른 크루의 크루장이 우리 크루원을 내보내려 해도 주문 서비스를 부르지 않고 403이다")
	void kickWithOtherCrewIdSkipsCheck() throws Exception {
		long otherCrewId = crewFacade.createCrew(outsiderId, "판교점심런").crewId();

		// outsider는 다른 크루의 크루장이지만 이 크루(crewId)에서는 크루원이 아니다
		remove(outsiderId, crewId, memberId)
			.andExpect(status().isForbidden());
		// 자기 크루 ID로 불러도 대상이 그 크루의 크루원이 아니라 404이고, 주문 서비스는 부르지 않는다
		remove(outsiderId, otherCrewId, memberId)
			.andExpect(status().isNotFound());

		verify(partyStatusPort, never()).findStatus(anyLong(), anyLong());
		assertThat(crewMemberRepository.existsByCrewIdAndMemberId(crewId, memberId)).isTrue();
	}

	private long joinMember() {
		return memberFacade.join(MemberFixture.joinRequest().build(identityStore)).getId();
	}

	private ResultActions leave(long requesterId, long targetCrewId) throws Exception {
		return mockMvc.perform(delete(LEAVE, targetCrewId).header(MemberIdHeader.NAME, requesterId));
	}

	private ResultActions remove(long requesterId, long targetCrewId, long targetMemberId) throws Exception {
		return mockMvc.perform(delete(CREW_MEMBER, targetCrewId, targetMemberId)
			.header(MemberIdHeader.NAME, requesterId));
	}
}
