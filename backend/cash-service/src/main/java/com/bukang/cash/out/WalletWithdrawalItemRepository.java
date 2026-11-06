package com.bukang.cash.out;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.cash.domain.withdrawal.WalletWithdrawal;
import com.bukang.cash.domain.withdrawal.WalletWithdrawalItem;
import com.bukang.cash.domain.withdrawal.WithdrawalItemStatus;

public interface WalletWithdrawalItemRepository extends JpaRepository<WalletWithdrawalItem, Long> {
	List<WalletWithdrawalItem> findByStatus(WithdrawalItemStatus status);

	List<WalletWithdrawalItem> findByWithdrawal(WalletWithdrawal withdrawal);

	List<WalletWithdrawalItem> findByStatusAndCreateDateBefore(WithdrawalItemStatus status, LocalDateTime threshold);

	boolean existsByWithdrawalAndStatusIn(WalletWithdrawal withdrawal, Collection<WithdrawalItemStatus> statuses);
}
