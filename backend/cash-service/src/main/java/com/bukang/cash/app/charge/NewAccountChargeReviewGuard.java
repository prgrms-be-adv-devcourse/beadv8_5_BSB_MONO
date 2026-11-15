package com.bukang.cash.app.charge;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.out.charge.WalletChargeRepository;

import lombok.RequiredArgsConstructor;

/**
 * 신규 계정 고액 충전 검토 대상 표시 (정책 4장 2차 2-1 「신규 회원 고액 충전 표시」).
 * 가입 후 24시간 안에 완료된 충전 누적이 한도를 처음 넘기는 순간, 그 충전에만 표시를 남긴다.
 * 충전을 막지는 않는다 — 표시만 해 둔다.
 */
@Component
@RequiredArgsConstructor
class NewAccountChargeReviewGuard {
	private static final long REVIEW_THRESHOLD = 500_000L;
	static final long WINDOW_HOURS = 24L;

	private final WalletChargeRepository walletChargeRepository;

	void checkAndFlag(WalletCharge completedCharge, CashMember member) {
		LocalDateTime windowStart = member.getCreateDate();
		LocalDateTime windowEnd = windowStart.plusHours(WINDOW_HOURS);
		// 이 충전 자체가 가입 후 24시간을 지나 시도된 것이면, 그 전 충전들의 누적이 한도를 넘겼더라도 표시하지 않는다
		if (completedCharge.getCreateDate().isAfter(windowEnd)) {
			return;
		}
		if (walletChargeRepository.existsByWalletAndReviewFlaggedTrue(completedCharge.getWallet())) {
			return;
		}
		long total = walletChargeRepository.sumCompletedAmountBetween(
			completedCharge.getWallet(), windowStart, windowEnd);
		if (total >= REVIEW_THRESHOLD) {
			completedCharge.markReviewFlagged();
		}
	}
}
