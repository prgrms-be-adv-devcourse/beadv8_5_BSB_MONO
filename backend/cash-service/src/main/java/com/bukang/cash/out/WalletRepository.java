package com.bukang.cash.out;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.cash.domain.wallet.Wallet;

import jakarta.persistence.LockModeType;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
	Optional<Wallet> findByHolderId(long cashMemberId);

	// 두 지갑을 같이 잠글 때는 호출하는 쪽이 ID가 작은 지갑부터 부른다 (교착 상태 방지, PRO-78)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select w from Wallet w where w.id = :id")
	Optional<Wallet> findByIdForUpdate(@Param("id") long id);
}
