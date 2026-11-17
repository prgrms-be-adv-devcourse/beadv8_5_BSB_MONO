package com.bukang.payout.out;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.bukang.payout.domain.PayoutAccount;
import com.bukang.payout.domain.PayoutItem;

public interface PayoutItemRepository extends JpaRepository<PayoutItem, Long> {
	// 원장 잔고: 판매자 × 대회 × 계정의 줄을 더한다 (PRO-214). 잔고는 따로 저장하지 않는다
	// 취소된 정산 내역의 줄도 반대 줄과 함께 더해져 0이 되므로 상태로 거르지 않는다
	@Query("""
		select coalesce(sum(i.amount), 0) from PayoutItem i
		where i.sellerId = :sellerId and i.raceId = :raceId and i.account = :account
		""")
	long sumBalance(long sellerId, long raceId, PayoutAccount account);

	// 판매자가 갚을 돈이 남은 대회 (탈퇴 제한, R11). 대회마다 따로 더한다. 입금이 겹쳐 0 아래가 된 대회가 다른 대회의 빚을 가리지 않게 한다
	@Query("""
		select i.raceId from PayoutItem i
		where i.sellerId = :sellerId and i.account = com.bukang.payout.domain.PayoutAccount.갚을_돈
		group by i.raceId
		having sum(i.amount) > 0
		""")
	List<Long> findRaceIdsWithReceivable(long sellerId);

	// 판매자 건별 정산 조회: 응답에 정산 ID · 상태를 함께 내려주므로 정산 내역을 한 번에 읽는다 (N+1 방지)
	// 원장 방식(PRO-214)에서는 참가권마다 판매 줄 하나만 보여 준다(같은 참가권이 두 번 보이지 않게). 응답 구조는 PRO-218에서 다시 정한다
	// 다대일(item → payout) fetch join이라 행 수가 늘지 않아 DB에서 페이지를 자를 수 있다
	// fetch join이 든 쿼리로는 개수를 셀 수 없으므로 개수 쿼리를 따로 적는다
	@Query(value = """
		select i from PayoutItem i join fetch i.payout p
		where p.sellerId = :sellerId and i.recordType = com.bukang.payout.domain.PayoutRecordType.판매
			and i.payoutCreateDate between :from and :to
		order by i.payoutCreateDate desc, i.id desc
		""",
		countQuery = """
			select count(i) from PayoutItem i join i.payout p
			where p.sellerId = :sellerId and i.recordType = com.bukang.payout.domain.PayoutRecordType.판매
				and i.payoutCreateDate between :from and :to
			""")
	Page<PayoutItem> findSellerItems(long sellerId, LocalDate from, LocalDate to, Pageable pageable);

	// 판매자 월 매출 참고 자료 (BUK-246): 판매 줄과 판매 취소 줄을 정산 생성일로 더한다
	// 판매 취소 줄은 원래 판매 줄의 정산 생성일을 그대로 가져가므로 같은 달 안에서 상쇄된다. 1차 정산금은 판매가 아니라 넣지 않는다
	@Query("""
		select coalesce(sum(i.amount), 0) from PayoutItem i
		where i.sellerId = :sellerId and i.payoutCreateDate between :from and :to
			and i.recordType in (com.bukang.payout.domain.PayoutRecordType.판매,
				com.bukang.payout.domain.PayoutRecordType.판매_취소)
		""")
	long sumSales(long sellerId, LocalDate from, LocalDate to);

	// 통신판매중개자 분기 판매 자료 (BUK-246, 부가가치세법 75조): 판매자마다 판매 건수 · 금액을 정산 생성일로 더한다
	// 판매 취소 줄은 건수 −1, 금액은 음수로 상쇄된다. 건수 · 금액이 모두 0인 판매자는 뺀다
	@Query("""
		select new com.bukang.payout.out.PayoutItemRepository$SellerSales(i.sellerId,
			sum(case when i.recordType = com.bukang.payout.domain.PayoutRecordType.판매 then 1 else -1 end),
			sum(i.amount))
		from PayoutItem i
		where i.payoutCreateDate between :from and :to
			and i.recordType in (com.bukang.payout.domain.PayoutRecordType.판매,
				com.bukang.payout.domain.PayoutRecordType.판매_취소)
		group by i.sellerId
		having sum(i.amount) <> 0
			or sum(case when i.recordType = com.bukang.payout.domain.PayoutRecordType.판매 then 1 else -1 end) <> 0
		order by i.sellerId
		""")
	List<SellerSales> sumSalesBySeller(LocalDate from, LocalDate to);

	record SellerSales(long sellerId, long salesCount, long salesAmount) {
	}
}
