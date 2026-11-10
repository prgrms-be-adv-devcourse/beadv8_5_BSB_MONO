package com.bukang.cash.app.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.bukang.cash.app.CashFacade;
import com.bukang.cash.domain.recovery.RecoveryDeposit;
import com.bukang.cash.domain.recovery.RecoveryDepositStatus;
import com.bukang.cash.dto.recovery.CashClaimVirtualAccountResponseDto;
import com.bukang.cash.out.pg.PaymentClient;
import com.bukang.cash.out.pg.VirtualAccountIssueResult;
import com.bukang.cash.out.recovery.RecoveryDepositRepository;

// 정산이 부르는 청구용 가상계좌 발급 유스케이스 테스트. PG는 목으로 바꿔 발급 결과를 직접 정한다
@SpringBootTest
@ActiveProfiles("test")
class CashRecoveryVirtualAccountUseCaseTest {
	private static final String CLAIM_CODE = "RC-20261214-0A3F";
	private static final LocalDateTime DUE_DATE = LocalDateTime.of(2027, 3, 14, 10, 0);

	@Autowired
	private CashFacade cashFacade;
	@Autowired
	private RecoveryDepositRepository recoveryDepositRepository;
	@MockitoBean
	private PaymentClient paymentClient;

	@AfterEach
	void tearDown() {
		recoveryDepositRepository.deleteAll();
	}

	@Test
	@DisplayName("처음 요청한 청구는 PG에서 가상계좌를 발급받아 입금 대기로 저장하고, 은행 이름과 계좌번호를 돌려준다")
	void issuesAndSavesOnFirstRequest() {
		given(paymentClient.issueVirtualAccount(CLAIM_CODE, 400_000))
			.willReturn(new VirtualAccountIssueResult("국민은행", "X6000123456789", DUE_DATE, "ps_secret_1"));

		CashClaimVirtualAccountResponseDto result = cashFacade.issueClaimVirtualAccount(CLAIM_CODE, 400_000);

		assertThat(result).isEqualTo(new CashClaimVirtualAccountResponseDto("국민은행", "X6000123456789"));
		RecoveryDeposit saved = recoveryDepositRepository.findByClaimCode(CLAIM_CODE).orElseThrow();
		assertThat(saved.getAmount()).isEqualTo(400_000);
		assertThat(saved.getBankName()).isEqualTo("국민은행");
		assertThat(saved.getAccountNumber()).isEqualTo("X6000123456789");
		assertThat(saved.getDueDate()).isEqualTo(DUE_DATE);
		assertThat(saved.getSecret()).isEqualTo("ps_secret_1");
		assertThat(saved.getStatus()).isEqualTo(RecoveryDepositStatus.WAITING);
	}

	@Test
	@DisplayName("같은 청구로 다시 요청하면 PG를 다시 부르지 않고 처음 발급한 계좌를 그대로 돌려준다")
	void returnsSavedAccountOnRepeatedRequest() {
		given(paymentClient.issueVirtualAccount(CLAIM_CODE, 400_000))
			.willReturn(new VirtualAccountIssueResult("국민은행", "X6000123456789", DUE_DATE, "ps_secret_1"));
		cashFacade.issueClaimVirtualAccount(CLAIM_CODE, 400_000);

		CashClaimVirtualAccountResponseDto result = cashFacade.issueClaimVirtualAccount(CLAIM_CODE, 400_000);

		assertThat(result).isEqualTo(new CashClaimVirtualAccountResponseDto("국민은행", "X6000123456789"));
		verify(paymentClient, times(1)).issueVirtualAccount(CLAIM_CODE, 400_000);
		assertThat(recoveryDepositRepository.count()).isEqualTo(1);
	}
}
