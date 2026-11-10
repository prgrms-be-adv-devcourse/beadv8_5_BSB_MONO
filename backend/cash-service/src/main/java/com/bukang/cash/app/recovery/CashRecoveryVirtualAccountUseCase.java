package com.bukang.cash.app.recovery;

import org.springframework.stereotype.Service;

import com.bukang.cash.domain.recovery.RecoveryDeposit;
import com.bukang.cash.dto.recovery.CashClaimVirtualAccountResponseDto;
import com.bukang.cash.out.pg.PaymentClient;
import com.bukang.cash.out.pg.VirtualAccountIssueResult;
import com.bukang.cash.out.recovery.RecoveryDepositRepository;

import lombok.RequiredArgsConstructor;

/**
 * 청구용 가상계좌 발급: 정산이 청구를 만들면 판매자가 갚을 돈을 받을 계좌를 PG에서 발급받아 회수 입금으로 저장한다
 * PG 호출은 트랜잭션 밖에서 한다. 응답을 기다리는 동안 DB 커넥션을 쥐지 않는다
 */
@Service
@RequiredArgsConstructor
public class CashRecoveryVirtualAccountUseCase {
	private final PaymentClient paymentClient;
	private final RecoveryDepositRepository recoveryDepositRepository;

	public CashClaimVirtualAccountResponseDto issue(String claimCode, long amount) {
		// 이미 발급한 청구면 PG를 다시 부르지 않는다. 정산이 응답을 못 받고 다시 불러도 판매자에게 알린 계좌가 바뀌지 않는다
		RecoveryDeposit deposit = recoveryDepositRepository.findByClaimCode(claimCode)
			.orElseGet(() -> issueAndSave(claimCode, amount));
		return new CashClaimVirtualAccountResponseDto(deposit.getBankName(), deposit.getAccountNumber());
	}

	private RecoveryDeposit issueAndSave(String claimCode, long amount) {
		VirtualAccountIssueResult issued = paymentClient.issueVirtualAccount(claimCode, amount);
		return recoveryDepositRepository.save(new RecoveryDeposit(
			claimCode, amount, issued.bankName(), issued.accountNumber(), issued.dueDate(), issued.secret()));
	}
}
