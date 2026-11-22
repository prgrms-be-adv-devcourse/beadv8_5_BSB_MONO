package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.stream.IntStream;

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
import com.bukang.member.domain.exception.WithdrawCheckFailedException;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.cash.CashBalancePort;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.out.order.ActiveTicketPort;
import com.bukang.member.support.MemberFixture;

// 관리자 회원 관리 「전체 회원」 닉네임 찾기 (CRU-156)
// 닉네임 앞부분으로 최대 20명, 개인정보 없이 예치금·참가권 수만 붙여 보여 준다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InMemoryIdentityVerificationStore.Config.class)
class ApiV1AdminMemberSearchTest {
	private static final String URL = "/api/v1/member/admin/members";

	@Autowired
	private InMemoryIdentityVerificationStore identityStore;
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private MemberFacade memberFacade;
	@Autowired
	private MemberRepository memberRepository;
	@Autowired
	private TermsAgreementRepository termsAgreementRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	private CashBalancePort cashBalancePort;
	@MockitoBean
	private ActiveTicketPort activeTicketPort;

	private long adminId;

	@BeforeEach
	void setUp() {
		adminId = join("관리자", "admin@crewrun.com");
		memberFacade.grantAdmin("admin@crewrun.com");
	}

	@AfterEach
	void tearDown() {
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("관리자가 아니면 403이다")
	void nonAdminIsForbidden() throws Exception {
		long memberId = join("달리는곰", "bear@crewrun.com");

		find(memberId, "달리는")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_ADMIN"));
	}

	@Test
	@DisplayName("회원 ID 헤더가 없으면 401이다")
	void loginRequired() throws Exception {
		mockMvc.perform(get(URL).param("nickname", "달리는"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("닉네임이 없거나 공백뿐이면 400이다")
	void blankNicknameIsBadRequest() throws Exception {
		mockMvc.perform(get(URL).header(MemberIdHeader.NAME, adminId))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		find(adminId, "   ")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("닉네임 앞부분이 같은 회원을 닉네임 순으로, 예치금·참가권 수와 함께 개인정보 없이 보여 준다")
	void findsByNicknamePrefix() throws Exception {
		long bear2 = join("달리는곰2", "bear2@crewrun.com");
		long bear = join("달리는곰", "bear@crewrun.com");
		join("빨리달리는곰", "fast@crewrun.com");
		given(cashBalancePort.findBalance(bear)).willReturn(45000L);
		given(activeTicketPort.countActiveTickets(bear)).willReturn(2);

		String body = find(adminId, " 달리는곰 ")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].id").value(bear))
			.andExpect(jsonPath("$.data[0].nickname").value("달리는곰"))
			.andExpect(jsonPath("$.data[0].joinedAt").exists())
			.andExpect(jsonPath("$.data[0].deposit").value(45000))
			.andExpect(jsonPath("$.data[0].activeTickets").value(2))
			.andExpect(jsonPath("$.data[1].id").value(bear2))
			.andReturn().getResponse().getContentAsString();

		// 이름 · 이메일 · 휴대폰은 응답에 담지 않는다
		assertThat(body).doesNotContain("bear@", "bear2@", "\"name\"", "phone", "email");
	}

	@Test
	@DisplayName("% 와 _ 는 글자 그대로 찾는다")
	void escapesLikeWildcards() throws Exception {
		join("곰_달려", "under@crewrun.com");
		join("곰a달려", "a@crewrun.com");

		find(adminId, "곰_")
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].nickname").value("곰_달려"));
		find(adminId, "%")
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	@DisplayName("탈퇴한 회원은 보여 주지 않는다")
	void excludesWithdrawn() throws Exception {
		long bear = join("달리는곰", "bear@crewrun.com");
		jdbcTemplate.update("update member_member set status = 'WITHDRAWN' where member_id = ?", bear);

		find(adminId, "달리는곰")
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	@DisplayName("최대 20명까지만 보여 준다")
	void limitsTwenty() throws Exception {
		IntStream.rangeClosed(10, 30).forEach(i -> join("곰러너" + i, "bear" + i + "@crewrun.com"));

		find(adminId, "곰러너")
			.andExpect(jsonPath("$.data.length()").value(20))
			.andExpect(jsonPath("$.data[19].nickname").value("곰러너29"));
	}

	@Test
	@DisplayName("예치금 서비스가 실패하면 예치금만 비우고 나머지는 보여 주며, 그 뒤 회원은 다시 묻지 않는다")
	void cashFailureLeavesDepositEmpty() throws Exception {
		join("달리는곰", "bear@crewrun.com");
		join("달리는곰2", "bear2@crewrun.com");
		given(cashBalancePort.findBalance(anyLong())).willThrow(new WithdrawCheckFailedException("모름"));
		given(activeTicketPort.countActiveTickets(anyLong())).willReturn(1);

		find(adminId, "달리는곰")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].deposit").doesNotExist())
			.andExpect(jsonPath("$.data[1].deposit").doesNotExist())
			.andExpect(jsonPath("$.data[0].activeTickets").value(1))
			.andExpect(jsonPath("$.data[1].activeTickets").value(1));

		verify(cashBalancePort, times(1)).findBalance(anyLong());
	}

	@Test
	@DisplayName("주문 서비스가 실패하면 참가권 수만 비운다")
	void orderFailureLeavesTicketsEmpty() throws Exception {
		join("달리는곰", "bear@crewrun.com");
		given(cashBalancePort.findBalance(anyLong())).willReturn(0L);
		given(activeTicketPort.countActiveTickets(anyLong())).willThrow(new WithdrawCheckFailedException("모름"));

		find(adminId, "달리는곰")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].deposit").value(0))
			.andExpect(jsonPath("$.data[0].activeTickets").doesNotExist());
	}

	private ResultActions find(long memberId, String nickname) throws Exception {
		return mockMvc.perform(get(URL).param("nickname", nickname).header(MemberIdHeader.NAME, memberId));
	}

	private long join(String nickname, String email) {
		return memberFacade.join(MemberFixture.joinRequest()
			.nickname(nickname)
			.email(email)
			.birthDate(LocalDate.of(1990, 1, 2))
			.build(identityStore)).getId();
	}
}
