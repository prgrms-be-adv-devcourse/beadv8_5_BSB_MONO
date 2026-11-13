package com.bukang.cash.app.recovery;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.bukang.cash.domain.exception.recovery.VirtualAccountIssueFailedException;
import com.bukang.cash.domain.recovery.RecoveryDeposit;
import com.bukang.cash.dto.recovery.CashClaimVirtualAccountResponseDto;
import com.bukang.cash.out.pg.PaymentClient;
import com.bukang.cash.out.pg.VirtualAccountIssueResult;
import com.bukang.cash.out.recovery.RecoveryDepositRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 청구용 가상계좌 발급: 정산이 청구를 만들면 판매자가 갚을 돈을 받을 계좌를 PG에서 발급받아 회수 입금으로 저장한다
 * PG 호출은 트랜잭션 밖에서 한다. 응답을 기다리는 동안 DB 커넥션을 쥐지 않는다
 */
@Slf4j
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

	// PG가 발급한 직후 저장하지 못했으면(응답 시간 초과, 서버 종료) 그 계좌는 PG에만 남아 있다.
	// 같은 주문번호로 다시 발급하면 계좌가 둘이 될 수 있어, 먼저 PG에서 찾고 없을 때만 발급한다
	private RecoveryDeposit issueAndSave(String claimCode, long amount) {
		Optional<VirtualAccountIssueResult> found = paymentClient.findVirtualAccount(claimCode);
		// 같은 토스 키를 쓰는 다른 환경(로컬·배포 서버)에서 같은 날 같은 청구 ID가 나오면 그 환경의 계좌가 찾아진다.
		// 금액이 다르면 이 청구의 계좌가 아니므로 쓰지 않는다. 금액까지 같으면 가려내지 못한다
		if (found.isPresent() && found.get().amount() != amount) {
			log.error("PG에 같은 주문번호로 금액이 다른 가상계좌가 있어 발급하지 않습니다. claimCode={}, 요청 금액={}, PG 계좌 금액={}",
				claimCode, amount, found.get().amount());
			throw new VirtualAccountIssueFailedException("PG에 같은 주문번호로 금액이 다른 가상계좌가 있습니다.");
		}
		VirtualAccountIssueResult issued = found.orElseGet(() -> paymentClient.issueVirtualAccount(claimCode, amount));
		try {
			return recoveryDepositRepository.save(new RecoveryDeposit(
				claimCode, amount, issued.bankName(), issued.accountNumber(), issued.dueDate(), issued.secret()));
		} catch (DataIntegrityViolationException exception) {
			// 같은 청구의 첫 요청 두 개가 동시에 와서 다른 쪽이 먼저 저장했다. 같은 청구의 다시 요청과 같으므로 먼저 저장된 계좌를 돌려준다
			// 그래도 없으면 청구 ID가 아닌 다른 제약 위반이라 그대로 던진다
			log.info("같은 청구의 다른 요청이 먼저 저장해 그 계좌를 돌려줍니다. claimCode={}", claimCode);
			return recoveryDepositRepository.findByClaimCode(claimCode).orElseThrow(() -> exception);
		}
	}
}
