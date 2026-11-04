package com.bukang.payout.out;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.bukang.payout.domain.PayoutHold;

public interface PayoutHoldRepository extends JpaRepository<PayoutHold, Long> {
	// 판매자 지급보류 내역 조회: [start, end) 범위. 끝 날의 23:59:59.xxx까지 넣으려고 끝은 다음 날 0시 미만으로 자른다
	// 응답에 정산 ID · 지급액을 함께 내려주므로 정산 내역을 한 번에 읽는다 (N+1 방지)
	@Query("""
		select h from PayoutHold h join fetch h.payout p
		where p.sellerId = :sellerId and h.heldAt >= :start and h.heldAt < :end
		order by h.heldAt desc, h.id desc
		""")
	List<PayoutHold> findSellerHolds(long sellerId, LocalDateTime start, LocalDateTime end);

}
