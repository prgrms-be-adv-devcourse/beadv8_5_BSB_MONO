package com.bukang.cash.out;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.domain.wallet.WalletLedger;

public interface WalletLedgerRepository extends JpaRepository<WalletLedger, Long> {
	@Query("select coalesce(sum(l.amount), 0) from WalletLedger l "
		+ "where l.wallet = :wallet and l.ledgerType = :ledgerType and l.createDate >= :from")
	long sumAmountSince(@Param("wallet") Wallet wallet, @Param("ledgerType") LedgerType ledgerType,
		@Param("from") LocalDateTime from);
}
