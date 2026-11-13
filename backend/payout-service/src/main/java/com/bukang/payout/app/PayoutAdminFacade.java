package com.bukang.payout.app;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.PayoutBatchTrigger;
import com.bukang.payout.domain.PayoutClaimStatus;
import com.bukang.payout.domain.PayoutCreateFailureType;
import com.bukang.payout.domain.PayoutHoldReason;
import com.bukang.payout.domain.PayoutQueryPeriod;
import com.bukang.payout.domain.exception.PayoutBatchFutureDateException;
import com.bukang.payout.dto.PayoutAdminClaimsDto;
import com.bukang.payout.dto.PayoutBatchRunDto;
import com.bukang.payout.dto.PayoutClaimDto;
import com.bukang.payout.dto.PayoutCreateFailuresDto;
import com.bukang.payout.dto.PayoutDto;
import com.bukang.payout.dto.PayoutHoldDto;
import com.bukang.payout.out.PayoutBatchRunRepository;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 API(지급보류 걸기·풀기, 청구 입금 확인, 이상 감지 기록 조회)의 진입점. 관리자인지 먼저 확인하고 UseCase를 부른다 (PRO-265)
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
	private final PayoutClaimUseCase payoutClaimUseCase;
	private final PayoutClaimQueryUseCase payoutClaimQueryUseCase;
	private final PayoutAdminQueryUseCase payoutAdminQueryUseCase;
	private final PayoutBatchUseCase payoutBatchUseCase;
	private final PayoutBatchRunRepository payoutBatchRunRepository;

	public PayoutHoldDto hold(long memberId, long payoutId, PayoutHoldReason reasonType, String reason,
		LocalDateTime now) {
		payoutAdminVerifyUseCase.verify(memberId);
		return payoutHoldUseCase.hold(payoutId, reasonType, reason, memberId, now);
	}

	public PayoutHoldDto release(long memberId, long holdId, LocalDateTime now) {
		payoutAdminVerifyUseCase.verify(memberId);
		return payoutHoldUseCase.release(holdId, memberId, now);
	}

	public PayoutDto findPayout(long memberId, String payoutCode) {
		payoutAdminVerifyUseCase.verify(memberId);
		return payoutHoldUseCase.findPayout(payoutCode);
	}

	// 청구 입금 확인: 판매자가 청구 금액을 보낸 것을 관리자가 확인한다 (선 환불 후 회수 R4 · R5)
	// 응답 DTO는 청구 트랜잭션이 커밋된 뒤에 만든다. PayoutClaim에 지연 로딩 연관이 없어 안전하다
	public PayoutClaimDto confirmClaim(long memberId, long claimId, LocalDateTime now) {
		payoutAdminVerifyUseCase.verify(memberId);
		return PayoutClaimDto.from(payoutClaimUseCase.confirm(claimId, memberId, now));
	}

	// 관리자 회수 관리 화면: 카드 숫자와 청구 목록 (BUK-107)
	public PayoutAdminClaimsDto claims(long memberId, PayoutClaimStatus status, String code, int page, int size,
		LocalDate today) {
		payoutAdminVerifyUseCase.verify(memberId);
		return payoutClaimQueryUseCase.claims(status, code, page, size, today);
	}

	// 이상 감지 기록 조회 (BUK-108). 관리자 확인 뒤 읽기 트랜잭션 하나만 연다
	public PayoutCreateFailuresDto createFailures(long memberId, LocalDate from, LocalDate to,
		PayoutCreateFailureType type, int page, int size) {
		payoutAdminVerifyUseCase.verify(memberId);
		return payoutAdminQueryUseCase.createFailures(from, to, type, page, size);
	}

	// 정산 생성 배치를 다시 돌린다 (BUK-167). 스케줄과 같은 잠금을 써서 이미 돌고 있으면 409다
	public List<PayoutBatchRunDto> runCreate(long memberId, LocalDate today, LocalDateTime now) {
		payoutAdminVerifyUseCase.verify(memberId);
		checkNotFuture(today, now);
		return payoutBatchUseCase.runCreate(PayoutBatchTrigger.관리자, memberId, today, now);
	}

	// 지급 배치(회수 청구 포함)를 다시 돌린다 (BUK-167)
	public List<PayoutBatchRunDto> runPay(long memberId, LocalDate today, LocalDateTime now) {
		payoutAdminVerifyUseCase.verify(memberId);
		checkNotFuture(today, now);
		return payoutBatchUseCase.runPay(PayoutBatchTrigger.관리자, memberId, today, now);
	}

	// 미래 기준일로 지급하면 지급일이 안 된 정산이 오늘 나간다 (BUK-167 리뷰). 생성도 같은 이유로 막는다
	private static void checkNotFuture(LocalDate today, LocalDateTime now) {
		if (today.isAfter(now.toLocalDate())) {
			throw new PayoutBatchFutureDateException();
		}
	}

	// 배치 실행 기록 (BUK-167). 기간은 실행 기준일 기준, 최대 1개월
	public List<PayoutBatchRunDto> batchRuns(long memberId, LocalDate from, LocalDate to) {
		payoutAdminVerifyUseCase.verify(memberId);
		PayoutQueryPeriod.checkBatchRunDays(from, to);
		return payoutBatchRunRepository.findByRunDateBetweenOrderByIdDesc(from, to).stream()
			.map(PayoutBatchRunDto::from)
			.toList();
	}
}
