package com.bukang.payout.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.PayoutHoldReason;
import com.bukang.payout.dto.PayoutClaimDto;
import com.bukang.payout.dto.PayoutHoldDto;
import com.bukang.payout.dto.PayoutRecoveryDto;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 API(지급보류 걸기·풀기, 회수 완료, 청구 입금 확인)의 진입점. 관리자인지 먼저 확인하고 UseCase를 부른다 (PRO-265)
 *
 * 관리자 확인은 회원 서비스를 HTTP로 부르므로, 응답을 기다리는 동안 DB 연결을 잡지 않게 트랜잭션 없이 실행하고
 * 각 UseCase가 자기 트랜잭션을 열고 커밋한다. 그래서 메서드마다 트랜잭션 UseCase는 하나만 부른다 (둘을 부르면 따로 커밋된다)
 * NOT_SUPPORTED는 바깥 트랜잭션을 멈출 뿐 그 DB 연결을 놓지는 않는다
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class PayoutAdminFacade {
	private final PayoutAdminVerifyUseCase payoutAdminVerifyUseCase;
	private final PayoutHoldUseCase payoutHoldUseCase;
	private final PayoutLedgerUseCase payoutLedgerUseCase;
	private final PayoutClaimUseCase payoutClaimUseCase;

	public PayoutHoldDto hold(long memberId, long payoutId, PayoutHoldReason reasonType, String reason,
		LocalDateTime now) {
		payoutAdminVerifyUseCase.verify(memberId);
		return payoutHoldUseCase.hold(payoutId, reasonType, reason, memberId, now);
	}

	public PayoutHoldDto release(long memberId, long holdId, LocalDateTime now) {
		payoutAdminVerifyUseCase.verify(memberId);
		return payoutHoldUseCase.release(holdId, memberId, now);
	}

	// 입금 확인 규칙은 원장 유스케이스가 맡는다 (payout.md 4-2, PRO-33).
	// 응답 DTO는 원장 트랜잭션이 커밋된 뒤에 만든다. PayoutRecovery에 지연 로딩 연관이 없어 안전하다
	public PayoutRecoveryDto completeRecovery(long memberId, long recoveryId, LocalDateTime now) {
		payoutAdminVerifyUseCase.verify(memberId);
		return PayoutRecoveryDto.from(payoutLedgerUseCase.confirmDeposit(recoveryId, memberId, now));
	}

	// 청구 입금 확인: 판매자가 청구 금액을 보낸 것을 관리자가 확인한다 (선 환불 후 회수 R4 · R5)
	// 응답 DTO는 청구 트랜잭션이 커밋된 뒤에 만든다. PayoutClaim에 지연 로딩 연관이 없어 안전하다
	public PayoutClaimDto confirmClaim(long memberId, long claimId, LocalDateTime now) {
		payoutAdminVerifyUseCase.verify(memberId);
		return PayoutClaimDto.from(payoutClaimUseCase.confirm(claimId, memberId, now));
	}
}
