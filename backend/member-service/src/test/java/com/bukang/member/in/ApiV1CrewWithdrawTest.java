package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bukang.common.shared.member.web.MemberIdHeader;
import com.bukang.member.app.MemberFacade;
import com.bukang.member.crew.app.CrewFacade;
import com.bukang.member.crew.out.CrewInviteRepository;
import com.bukang.member.crew.out.CrewMemberRepository;
import com.bukang.member.crew.out.CrewRepository;
import com.bukang.member.domain.MemberStatus;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.RefreshTokenRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.cash.CashBalancePort;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.out.order.ActiveTicketPort;
import com.bukang.member.support.MemberFixture;

// 회원 탈퇴와 크루 (BE-029): 크루장이면 409, 아니면 같은 트랜잭션에서 모든 크루원 행을 지운다
// 예치금·주문 서비스와 Redis는 띄우지 않으므로 포트와 Refresh Token 저장소를 목으로 바꾼다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InMemoryIdentityVerificationStore.Config.class)
class ApiV1CrewWithdrawTest {
	@Autowired
	private InMemoryIdentityVerificationStore identityStore;

	private static final String ME = "/api/v1/member/members/me";

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

	@MockitoBean
	private CashBalancePort cashBalancePort;
	@MockitoBean
	private ActiveTicketPort activeTicketPort;
	@MockitoBean
	private RefreshTokenRepository refreshTokenRepository;

	private long leaderId;
	private long memberId;
	private long crewId;

	@BeforeEach
	void setUp() {
		leaderId = memberFacade.join(MemberFixture.joinRequest().build(identityStore)).getId();
		memberId = memberFacade.join(MemberFixture.joinRequest().build(identityStore)).getId();
		crewId = crewFacade.createCrew(leaderId, "새벽한강크루").crewId();
		crewFacade.join(memberId, crewFacade.createInvite(leaderId, crewId).inviteToken());
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
	@DisplayName("크루장인 크루가 있으면 409 MEMBER_WITHDRAW_CREW_LEADER이고 아무것도 바꾸지 않는다")
	void leaderCannotWithdraw() throws Exception {
		mockMvc.perform(delete(ME).header(MemberIdHeader.NAME, leaderId))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MEMBER_WITHDRAW_CREW_LEADER"))
			.andExpect(jsonPath("$.message")
				.value("크루장인 크루가 있어 탈퇴할 수 없습니다. 크루장을 넘기거나 크루를 없앤 뒤 탈퇴해주세요."));

		assertThat(memberRepository.findById(leaderId).orElseThrow().getStatus()).isNotEqualTo(MemberStatus.WITHDRAWN);
		assertThat(crewMemberRepository.existsByCrewIdAndMemberId(crewId, leaderId)).isTrue();
		// 크루장 확인은 예치금·참가권 확인보다 먼저라 다른 서비스를 부르지 않는다
		verify(cashBalancePort, never()).findBalance(leaderId);
		verify(refreshTokenRepository, never()).deleteAll(leaderId);
	}

	@Test
	@DisplayName("크루장이 아닌 회원이 탈퇴하면 모든 크루의 크루원 행을 지우고 크루원 수에서 빠진다")
	void memberWithdrawLeavesCrews() throws Exception {
		mockMvc.perform(delete(ME).header(MemberIdHeader.NAME, memberId))
			.andExpect(status().isOk());

		assertThat(memberRepository.findById(memberId).orElseThrow().getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
		assertThat(crewMemberRepository.findAllByMemberIdOrderByJoinedAtAscIdAsc(memberId)).isEmpty();
		assertThat(crewMemberRepository.countByCrewId(crewId)).isEqualTo(1);
		// 크루와 크루장은 그대로다
		assertThat(crewMemberRepository.existsByCrewIdAndMemberId(crewId, leaderId)).isTrue();
	}
}
