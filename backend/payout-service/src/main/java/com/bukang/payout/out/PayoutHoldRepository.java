package com.bukang.payout.out;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.bukang.payout.domain.PayoutHold;
import com.bukang.payout.domain.PayoutHoldStatus;

public interface PayoutHoldRepository extends JpaRepository<PayoutHold, Long> {
	// 판매자 지급보류 내역 조회: [start, end) 범위. 끝 날의 23:59:59.xxx까지 넣으려고 끝은 다음 날 0시 미만으로 자른다
	// 응답에 정산 ID · 지급액을 함께 내려주므로 정산 내역을 한 번에 읽는다 (N+1 방지)
	@Query("""
		select h from PayoutHold h join fetch h.payout p
		where p.sellerId = :sellerId and h.heldAt >= :start and h.heldAt < :end
		order by h.heldAt desc, h.id desc
		""")
	List<PayoutHold> findSellerHolds(long sellerId, LocalDateTime start, LocalDateTime end);

	// 관리자 지급보류 목록 (BUK-168). 줄마다 정산 ID · 판매자 · 지급액을 보여 주므로 정산을 한 번에 읽는다 (N+1 방지)
	// status가 null이면 전체. 최근 보류부터
	@Query(value = """
		select h from PayoutHold h join fetch h.payout
		where (:status is null or h.status = :status)
		order by h.heldAt desc, h.id desc""",
		countQuery = "select count(h) from PayoutHold h where (:status is null or h.status = :status)")
	Page<PayoutHold> findAdminHolds(PayoutHoldStatus status, Pageable pageable);

	// 관리자 ID 찾기: HD-면 그 보류, ST-면 그 정산에 걸린 보류 전부. 두 코드 모두 유일하고 접두사가 달라 섞이지 않는다
	@Query("""
		select h from PayoutHold h join fetch h.payout p
		where h.holdCode = :code or p.payoutCode = :code
		order by h.heldAt desc, h.id desc""")
	List<PayoutHold> findAdminHoldsByCode(String code);

	long countByStatus(PayoutHoldStatus status);

	// 취소로 닫힌 보류도 releasedAt을 채우므로 상태로 해제만 고른다
	long countByStatusAndReleasedAtGreaterThanEqualAndReleasedAtLessThan(PayoutHoldStatus status, LocalDateTime start,
		LocalDateTime end);

	// 카드 「보류 금액」: 진행 중 보류가 걸린 정산의 지급액 합. 보류가 여럿 걸린 정산도 한 번만 더한다 (11/17 결정)
	@Query("""
		select coalesce(sum(p.payoutAmount), 0) from Payout p
		where exists (select h.id from PayoutHold h
			where h.payout = p and h.status = com.bukang.payout.domain.PayoutHoldStatus.지급보류)""")
	long sumHeldPayoutAmount();

	// 관리자 정산 목록: 한 페이지 정산들의 진행 중 보류를 한 번에 읽는다
	List<PayoutHold> findByPayoutIdInAndStatus(Collection<Long> payoutIds, PayoutHoldStatus status);

	// 판매자가 정산 계좌를 등록하면 풀 자동 보류: 계좌 미등록으로 걸려 아직 지급보류인 것
	// 취소로 닫힌 정산의 보류는 지급보류가 아니라 빠진다. 지급보류인 것만 풀어 같은 이벤트를 다시 받아도 안전하다
	@Query("""
		select h from PayoutHold h join fetch h.payout p
		where p.sellerId = :sellerId
			and h.reasonType = com.bukang.payout.domain.PayoutHoldReason.정산_계좌_미등록
			and h.status = com.bukang.payout.domain.PayoutHoldStatus.지급보류
			and p.status = com.bukang.payout.domain.PayoutStatus.지급보류
		""")
	List<PayoutHold> findReleasableAccountHolds(long sellerId);
}
