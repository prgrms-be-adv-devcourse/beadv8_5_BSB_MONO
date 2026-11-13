package com.bukang.cash.in;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.domain.exception.admin.MemberServiceUnavailableException;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.out.charge.WalletChargeRepository;
import com.bukang.cash.out.member.CashMemberRepository;
import com.bukang.cash.out.member.MemberAdminPort;
import com.bukang.cash.out.wallet.WalletRepository;
import com.bukang.common.shared.member.web.MemberIdHeader;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiV1AdminCashControllerTest {
	private static final String URL = "/api/v1/cash/admin/charge-reviews";
	private static final long ADMIN_ID = 1L;

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private CashMemberRepository cashMemberRepository;
	@Autowired
	private WalletRepository walletRepository;
	@Autowired
	private WalletChargeRepository walletChargeRepository;

	@MockitoBean
	private MemberAdminPort memberAdminPort;

	// 파사드가 바깥 트랜잭션을 멈추고 조회 트랜잭션을 새로 열어, 테스트 트랜잭션 안의 데이터는 보이지 않는다. 커밋하고 지운다
	@AfterEach
	void tearDown() {
		walletChargeRepository.deleteAll();
		walletRepository.deleteAll();
		cashMemberRepository.deleteAll();
	}

	@Test
	@DisplayName("관리자면 200과 페이지 형식으로 고액 충전 확인 목록을 돌려준다")
	void list() throws Exception {
		given(memberAdminPort.isAdmin(ADMIN_ID)).willReturn(true);
		LocalDateTime now = LocalDateTime.now();
		CashMember member = cashMemberRepository.save(new CashMember(21, now.minusHours(1), now, "러너21"));
		Wallet wallet = walletRepository.save(new Wallet(member));
		WalletCharge charge = new WalletCharge(wallet, 300_000, "TOSS", "order_1");
		charge.markCompleted("toss_1", now, now);
		charge.markReviewFlagged();
		walletChargeRepository.save(charge);

		mockMvc.perform(get(URL).header(MemberIdHeader.NAME, String.valueOf(ADMIN_ID)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("고액 충전 확인 목록을 조회했습니다."))
			.andExpect(jsonPath("$.data.content[0].memberId").value(21))
			.andExpect(jsonPath("$.data.content[0].nickname").value("러너21"))
			.andExpect(jsonPath("$.data.content[0].chargeAmount").value(300_000))
			.andExpect(jsonPath("$.data.content[0].joinWindowChargeTotal").value(300_000))
			.andExpect(jsonPath("$.data.size").value(20))
			.andExpect(jsonPath("$.data.totalElements").value(1));
	}

	@Test
	@DisplayName("관리자가 아니면 403을 돌려준다")
	void notAdmin() throws Exception {
		given(memberAdminPort.isAdmin(ADMIN_ID)).willReturn(false);

		mockMvc.perform(get(URL).header(MemberIdHeader.NAME, String.valueOf(ADMIN_ID)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CASH_ADMIN_FORBIDDEN"))
			.andExpect(jsonPath("$.message").value("관리자만 이용할 수 있습니다."));
	}

	@Test
	@DisplayName("회원 서비스에 관리자 여부를 묻지 못하면 503을 돌려준다")
	void memberServiceUnavailable() throws Exception {
		given(memberAdminPort.isAdmin(ADMIN_ID)).willThrow(new MemberServiceUnavailableException());

		mockMvc.perform(get(URL).header(MemberIdHeader.NAME, String.valueOf(ADMIN_ID)))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("CASH_MEMBER_SERVICE_UNAVAILABLE"));
	}

	@Test
	@DisplayName("회원 ID 헤더가 없으면 401을 돌려준다")
	void loginRequired() throws Exception {
		mockMvc.perform(get(URL))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
	}
}
