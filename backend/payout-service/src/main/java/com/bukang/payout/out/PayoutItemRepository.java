package com.bukang.payout.out;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.bukang.payout.domain.PayoutAccount;
import com.bukang.payout.domain.PayoutItem;

public interface PayoutItemRepository extends JpaRepository<PayoutItem, Integer> {
	// 원장 잔고: 판매자 × 대회 × 계정의 줄을 더한다 (PRO-214). 잔고는 따로 저장하지 않는다
	// 취소된 정산 내역의 줄도 반대 줄과 함께 더해져 0이 되므로 상태로 거르지 않는다
	@Query("""
		select coalesce(sum(i.amount), 0) from PayoutItem i
		where i.sellerId = :sellerId and i.raceId = :raceId and i.account = :account
		""")
	long sumBalance(int sellerId, int raceId, PayoutAccount account);

	// 판매자 건별 정산 조회: 응답에 정산 ID · 상태를 함께 내려주므로 정산 내역을 한 번에 읽는다 (N+1 방지)
	// 원장 방식(PRO-214)에서는 참가권마다 판매 줄 하나만 보여 준다(같은 참가권이 두 번 보이지 않게). 응답 구조는 PRO-218에서 다시 정한다
	// 다대일(item → payout) fetch join이라 행 수가 늘지 않아 DB에서 페이지를 자를 수 있다
	// fetch join이 든 쿼리로는 개수를 셀 수 없으므로 개수 쿼리를 따로 적는다
	@Query(value = """
		select i from PayoutItem i join fetch i.payout p
		where p.sellerId = :sellerId and i.recordType = com.bukang.payout.domain.PayoutRecordType.SALE
			and i.targetDate between :from and :to
		order by i.targetDate desc, i.id desc
		""",
		countQuery = """
			select count(i) from PayoutItem i join i.payout p
			where p.sellerId = :sellerId and i.recordType = com.bukang.payout.domain.PayoutRecordType.SALE
				and i.targetDate between :from and :to
			""")
	Page<PayoutItem> findSellerItems(int sellerId, LocalDate from, LocalDate to, Pageable pageable);
}
