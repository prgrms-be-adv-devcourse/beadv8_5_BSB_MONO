package com.bukang.cash.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.config.CashWalletProperties;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.recovery.RecoveryDeposit;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.out.member.CashMemberRepository;
import com.bukang.cash.out.pg.PaymentApprovalResult;
import com.bukang.cash.out.pg.PaymentClient;
import com.bukang.cash.out.pg.PaymentLookupResult;
import com.bukang.cash.out.recovery.RecoveryDepositRepository;
import com.bukang.cash.out.wallet.WalletLedgerRepository;
import com.bukang.cash.out.wallet.WalletRepository;

// 토스가 부르는 가상계좌 입금 알림 웹훅 API 테스트. 토스는 200을 받을 때까지 같은 알림을 다시 보낸다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TossWebhookControllerTest {
	private static final String CLAIM_CODE = "RC-20261214-0A3F";
	private static final String SECRET = "ps_secret_1";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private CashWalletProperties cashWalletProperties;
	@Autowired
	private CashMemberRepository cashMemberRepository;
	@Autowired
	private WalletRepository walletRepository;
	@Autowired
	private WalletLedgerRepository walletLedgerRepository;
	@Autowired
	private RecoveryDepositRepository recoveryDepositRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;
	@MockitoBean
	private PaymentClient paymentClient;

	@BeforeEach
	void setUp() {
		transactionTemplate.executeWithoutResult(status -> {
			LocalDateTime now = LocalDateTime.of(2026, 12, 1, 10, 0);
			CashMember holding = cashMemberRepository.save(
				new CashMember(cashWalletProperties.holdingMemberId(), now, now, "홀딩"));
			walletRepository.save(new Wallet(holding));
		});
		recoveryDepositRepository.save(new RecoveryDeposit(
			CLAIM_CODE, 400_000, "국민은행", "X6000123456789", LocalDateTime.of(2027, 3, 14, 10, 0), SECRET));
		given(paymentClient.lookupByOrderId(CLAIM_CODE)).willReturn(PaymentLookupResult.completed(
			new PaymentApprovalResult(400_000, "pay_key_1", LocalDateTime.of(2026, 12, 20, 14, 2))));
	}

	@AfterEach
	void tearDown() {
		walletLedgerRepository.deleteAll();
		walletRepository.deleteAll();
		cashMemberRepository.deleteAll();
		recoveryDepositRepository.deleteAll();
	}

	@Test
	@DisplayName("토스 입금 알림을 받으면 200으로 답하고 홀딩 지갑에 회수 금액을 기록한다")
	void recordsDepositAndAnswersOk() throws Exception {
		depositCallback(SECRET).andExpect(status().isOk());

		assertThat(holdingBalance()).isEqualTo(400_000);
	}

	@Test
	@DisplayName("비밀값이 틀린 알림에도 200으로 답하고 기록하지 않는다")
	void answersOkWithoutRecordingForWrongSecret() throws Exception {
		depositCallback("forged_secret").andExpect(status().isOk());

		assertThat(holdingBalance()).isZero();
	}

	// 토스 DEPOSIT_CALLBACK 본문 모양. 우리가 쓰지 않는 칸도 함께 온다
	private ResultActions depositCallback(String secret) throws Exception {
		return mockMvc.perform(post("/api/v1/cash/webhooks/toss/deposit")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{ "createdAt": "2026-12-20T14:02:00.000000", "secret": "%s", "status": "DONE",
				"transactionKey": "txn_1", "orderId": "%s" }
				""".formatted(secret, CLAIM_CODE)));
	}

	private long holdingBalance() {
		return walletRepository.findById(cashWalletProperties.holdingMemberId()).orElseThrow().getBalance();
	}
}
