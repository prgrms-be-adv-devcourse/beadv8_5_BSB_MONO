package com.bukang.cash.app.payment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.cash.app.CashSupport;
import com.bukang.cash.domain.charge.CashChargePolicy;
import com.bukang.cash.domain.charge.ChargeStatus;
import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.dto.payment.RefundableChargeDto;
import com.bukang.cash.dto.payment.RefundableChargesResponseDto;
import com.bukang.cash.out.charge.WalletChargeRepository;

import lombok.RequiredArgsConstructor;

/**
 * 환급 화면용 충전 내역 조회
 * 정책 5장 2차 1 「PG 결제취소와 기한」: 결제는 오래된 충전부터 쓴 것으로 보므로, 남은 예치금을 최근 충전부터 채운다
 */
@Service
@RequiredArgsConstructor
public class CashRefundableChargeQueryUseCase {
	private final CashSupport cashSupport;
	private final WalletChargeRepository walletChargeRepository;

	@Transactional(readOnly = true)
	public RefundableChargesResponseDto findRefundableCharges(long memberId, LocalDateTime now) {
		Wallet wallet = cashSupport.findWalletByHolderId(memberId);

		List<WalletCharge> latestFirst = walletChargeRepository.findByWalletAndStatus(wallet, ChargeStatus.COMPLETED)
			.stream()
			.sorted(Comparator.comparing(WalletCharge::approvedAtOrChargeDate)
				.thenComparing(WalletCharge::getId)
				.reversed())
			.toList();

		long unfilled = wallet.getBalance();
		List<RefundableChargeDto> charges = new ArrayList<>();

		for (WalletCharge charge : latestFirst) {
			// 이미 PG 결제취소로 돌려준 몫은 다시 돌려줄 수 없어서 채울 수 있는 한도에서 뺀다
			long remaining = Math.min(charge.getAmount() - charge.getCanceledAmount(), unfilled);
			unfilled -= remaining;
			LocalDateTime approvedAt = charge.approvedAtOrChargeDate();
			charges.add(new RefundableChargeDto(
				charge.getId(),
				approvedAt,
				charge.getAmount(),
				remaining,
				CashChargePolicy.lastCancelableDate(approvedAt),
				!CashChargePolicy.isCancelable(approvedAt, now)));
		}

		LocalDate earliest = charges.stream()
			.filter(charge -> charge.remainingAmount() > 0 && !charge.expired())
			.map(RefundableChargeDto::lastCancelableDate)
			.min(Comparator.naturalOrder())
			.orElse(null);

		return new RefundableChargesResponseDto(wallet.getBalance(), earliest, charges);
	}
}
