package com.bukang.cash.out;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.cash.domain.withdrawal.WalletWithdrawal;
import com.bukang.cash.domain.withdrawal.WalletWithdrawalItem;
import com.bukang.cash.domain.withdrawal.WithdrawalItemStatus;

public interface WalletWithdrawalItemRepository extends JpaRepository<WalletWithdrawalItem, Long> {
	List<WalletWithdrawalItem> findByStatus(WithdrawalItemStatus status);

	boolean existsByWithdrawalAndStatus(WalletWithdrawal withdrawal, WithdrawalItemStatus status);
}
