package com.bukang.cash.app;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.cash.domain.wallet.LedgerCategory;
import com.bukang.cash.domain.wallet.LedgerRefType;
import com.bukang.cash.dto.PageDto;
import com.bukang.cash.dto.RefundableChargeDto;
import com.bukang.cash.dto.WalletLedgerItemDto;
import com.bukang.cash.out.WalletLedgerQueryRepository;

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
		Map<Long, RefundableChargeDto> chargesById = cashRefundableChargeQueryUseCase
			.findRefundableCharges(memberId, now)
			.charges()
			.stream()
			.collect(Collectors.toMap(RefundableChargeDto::chargeId, Function.identity()));
		return PageDto.from(
			walletLedgerQueryRepository.findByWallet(walletId, category, PageRequest.of(page, size)),
			ledger -> WalletLedgerItemDto.from(ledger,
				ledger.getRefTypeCode() == LedgerRefType.WalletCharge ? chargesById.get(ledger.getRefId()) : null));
	}
}
