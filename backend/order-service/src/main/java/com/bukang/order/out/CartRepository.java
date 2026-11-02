package com.bukang.order.out;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.order.domain.Cart;

import jakarta.persistence.LockModeType;

public interface CartRepository extends JpaRepository<Cart, Long> {
	Optional<Cart> findByMemberId(long memberId);

	boolean existsByMemberId(long memberId);

	// 담기를 한 요청씩 처리해, 같은 회원이 동시에 다른 종목을 담아도 한 종목만 남게 한다 (PRO-192)
	// 없는 장바구니를 이것으로 읽으면 MySQL이 gap lock을 잡아 동시 첫 담기가 데드락이 나므로, 있는 장바구니에만 쓴다
	// 항목을 join fetch로 같이 읽으면 H2에서는 FOR UPDATE가 빠지므로 장바구니 행만 잠근다 (항목은 잠근 뒤 지연 로딩)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Cart c where c.memberId = :memberId")
	Optional<Cart> findByMemberIdForUpdate(@Param("memberId") long memberId);
}
