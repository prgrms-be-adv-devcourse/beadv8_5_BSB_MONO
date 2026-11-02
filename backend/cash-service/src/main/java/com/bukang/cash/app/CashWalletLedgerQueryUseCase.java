package com.bukang.cash.app;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.cash.domain.wallet.LedgerCategory;
import com.bukang.cash.dto.PageDto;
import com.bukang.cash.dto.WalletLedgerItemDto;
import com.bukang.cash.out.WalletLedgerQueryRepository;

import lombok.RequiredArgsConstructor;

/**
 * 예치금 내역 조회 (PRO-258). 본인 지갑의 WalletLedger만 최신순으로, category로 탭 필터링해 조회한다
 */
@Service
@RequiredArgsConstructor
public class CashWalletLedgerQueryUseCase {
	private final CashSupport cashSupport;
	private final WalletLedgerQueryRepository walletLedgerQueryRepository;

	@Transactional(readOnly = true)
	public PageDto<WalletLedgerItemDto> findLedgers(long memberId, LedgerCategory category, int page, int size) {
		long walletId = cashSupport.findWalletByHolderId(memberId).getId();
		return PageDto.from(
			walletLedgerQueryRepository.findByWallet(walletId, category, PageRequest.of(page, size)),
			WalletLedgerItemDto::from);
	}
}
