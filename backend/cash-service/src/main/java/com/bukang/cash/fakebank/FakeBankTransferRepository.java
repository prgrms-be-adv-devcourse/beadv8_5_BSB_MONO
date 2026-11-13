package com.bukang.cash.fakebank;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FakeBankTransferRepository extends JpaRepository<FakeBankTransfer, Long> {
	Optional<FakeBankTransfer> findByTransferId(String transferId);
}
