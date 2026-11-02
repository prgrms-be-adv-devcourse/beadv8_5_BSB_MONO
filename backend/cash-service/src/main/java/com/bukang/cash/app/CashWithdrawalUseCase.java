package com.bukang.cash.app;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.bukang.cash.domain.exception.WalletBalanceEmptyException;
import com.bukang.cash.domain.exception.WalletNotFoundException;
import com.bukang.cash.domain.exception.WithdrawalMemberMismatchException;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.domain.withdrawal.WalletWithdrawal;
import com.bukang.cash.dto.CashWithdrawalResponseDto;
import com.bukang.cash.out.WalletLedgerRepository;
import com.bukang.cash.out.WalletRepository;
import com.bukang.cash.out.WalletWithdrawalRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CashWithdrawalUseCase {
	private final WalletWithdrawalRepository walletWithdrawalRepository;
	private final WalletRepository walletRepository;
	private final WalletLedgerRepository walletLedgerRepository;

	public CashWithdrawalResponseDto requestWithdrawal(
		long customerId, String bankCode, String accountNumber, String accountHolder, String idempotencyKey
	) {
		Wallet customerWallet = walletRepository.findByIdForUpdate(customerId)
			.orElseThrow(() -> new WalletNotFoundException("존재하지 않는 지갑입니다."));

		Optional<WalletWithdrawal> existing = walletWithdrawalRepository.findByIdempotencyKey(idempotencyKey);
		if (existing.isPresent()) {
			if (existing.get().getWallet().getId() != customerWallet.getId()) {
				throw new WithdrawalMemberMismatchException("이 Idempotency-Key로 환급한 회원과 요청한 회원이 다릅니다.");
			}
			return toDto(existing.get(), customerWallet.getBalance());
		}

		long amount = customerWallet.getBalance();
		if (amount <= 0) {
			throw new WalletBalanceEmptyException("환급할 잔액이 없습니다.");
		}

		WalletWithdrawal withdrawal = walletWithdrawalRepository.save(
			new WalletWithdrawal(customerWallet, amount, bankCode, accountNumber, accountHolder, idempotencyKey));
		walletLedgerRepository.save(customerWallet.debit(amount, LedgerType.출금__통장입금, withdrawal));
		withdrawal.markCompleted(LocalDateTime.now());

		return toDto(withdrawal, customerWallet.getBalance());
	}

	private CashWithdrawalResponseDto toDto(WalletWithdrawal withdrawal, long balance) {
		return CashWithdrawalResponseDto.of(withdrawal.getAmount(), balance);
	}
}
