package com.bukang.cash.app.wallet;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.cash.app.CashSupport;
import com.bukang.cash.app.payment.CashRefundableChargeQueryUseCase;
import com.bukang.cash.domain.wallet.LedgerCategory;
import com.bukang.cash.domain.wallet.LedgerRefType;
import com.bukang.cash.domain.wallet.WalletLedger;
import com.bukang.cash.dto.PageDto;
import com.bukang.cash.dto.payment.RefundableChargeDto;
import com.bukang.cash.dto.wallet.WalletLedgerItemDto;
import com.bukang.cash.out.wallet.WalletLedgerQueryRepository;

import lombok.RequiredArgsConstructor;

/**
 * 예치금 내역 조회 (PRO-258). 본인 지갑의 WalletLedger만 최신순으로, category로 탭 필터링해 조회한다
 * 충전 줄에는 환급 화면과 같은 계산으로 남은 금액·카드 취소 마지막 날을 붙인다 (정책 5장 2차 1 「PG 결제취소와 기한」)
 */
@Service
@RequiredArgsConstructor
public class CashWalletLedgerQueryUseCase {
	private final CashSupport cashSupport;
	private final WalletLedgerQueryRepository walletLedgerQueryRepository;
	private final CashRefundableChargeQueryUseCase cashRefundableChargeQueryUseCase;

	@Transactional(readOnly = true)
	public PageDto<WalletLedgerItemDto> findLedgers(
		long memberId, LedgerCategory category, int page, int size, LocalDateTime now
	) {
		long walletId = cashSupport.findWalletByHolderId(memberId).getId();
		Page<WalletLedger> ledgers =
			walletLedgerQueryRepository.findByWallet(walletId, category, PageRequest.of(page, size));
		// 남은 금액은 충전 줄에만 붙으므로, 이 페이지에 충전 줄이 없으면 충전 내역을 읽지 않는다
		boolean hasChargeRow = ledgers.stream()
			.anyMatch(ledger -> ledger.getRefTypeCode() == LedgerRefType.WalletCharge);
		Map<Long, RefundableChargeDto> chargesById = hasChargeRow
			? cashRefundableChargeQueryUseCase.findRefundableCharges(memberId, now)
				.charges()
				.stream()
				.collect(Collectors.toMap(RefundableChargeDto::chargeId, Function.identity()))
			: Map.of();
		return PageDto.from(
			ledgers,
			ledger -> WalletLedgerItemDto.from(ledger,
				ledger.getRefTypeCode() == LedgerRefType.WalletCharge ? chargesById.get(ledger.getRefId()) : null));
	}
}
