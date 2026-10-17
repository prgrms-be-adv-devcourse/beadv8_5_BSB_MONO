package com.bukang.payout.out;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.domain.PayoutStatus;

import jakarta.persistence.LockModeType;

public interface PayoutRepository extends JpaRepository<Payout, Integer> {
	// 정산 생성 배치: 이미 만든 정산 내역인지 확인한다. 유일 조건 (seller_id, race_id, payout_month, payout_stage)과 같은 키다
	boolean existsBySellerIdAndRaceIdAndPayoutMonthAndPayoutStage(int sellerId, int raceId, String payoutMonth,
		PayoutStage payoutStage);

	// 1차 정산 생성: 같은 판매자 × 대회의 최종 정산이 이미 있는지 본다 (최종이 먼저 생겼으면 1차를 만들지 않는다)
	boolean existsBySellerIdAndRaceIdAndPayoutStage(int sellerId, int raceId, PayoutStage payoutStage);

	// 최종 정산 생성: 같은 판매자 × 대회의 1차 정산 내역 (1차 정산금 차감과 보류 여부를 읽는다)
	// 1차 정산 월은 환불 마감일로 정해지고 마감일은 움직이지 않으므로 하나뿐이다
	Optional<Payout> findFirstBySellerIdAndRaceIdAndPayoutStageOrderByIdAsc(int sellerId, int raceId,
		PayoutStage payoutStage);

	// 회수 상계: 같은 판매자 × 대회의 지급 전 정산 내역을 잠가서 읽는다 (만든 순서대로, 1차가 먼저)
	// 회수 요청 · 최종 생성 · 지급 배치가 같은 정산 내역 금액을 동시에 바꾸지 않게 한다 (PRO-214)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select p from Payout p
		where p.sellerId = :sellerId and p.raceId = :raceId and p.status in :statuses
		order by p.id
		""")
	List<Payout> findForUpdate(int sellerId, int raceId, Collection<PayoutStatus> statuses);

	// 대회 취소 처리: 그 대회의 정산 내역 전부 (지급 전은 취소로 닫고, 지급 완료는 회수액에 더한다)
	List<Payout> findByRaceId(int raceId);

	// 지급 배치: 지급 예정일이 오늘이거나 지난 정산 내역 (지난 날을 놓쳐도 다음 실행에서 따라잡는다)
	List<Payout> findByStatusAndScheduledPayDateLessThanEqual(PayoutStatus status, LocalDate date);

	// 판매자 정산 내역 조회: 정산 월(YYYY-MM)은 문자열이어도 자릿수가 같아 사전순 비교가 달 순서와 같다
	// 유일 조건 (seller_id, race_id, payout_month)의 인덱스는 seller_id로 시작하므로 판매자 범위를 이 인덱스로 찾는다
	List<Payout> findBySellerIdAndPayoutMonthBetweenOrderByPayoutMonthDescIdDesc(
		int sellerId, String fromMonth, String toMonth);

	// 판매자 요약: 지급 예정인 정산 내역 중 가장 이른 지급 예정일 (다음 정산일)
	Optional<Payout> findFirstBySellerIdAndStatusOrderByScheduledPayDateAsc(int sellerId, PayoutStatus status);

	// 판매자 요약: 그 지급일에 나갈 지급액 합계 (정산 예정액). 보류(HELD)된 정산 내역은 상태가 달라 빠진다
	@Query("""
		select coalesce(sum(p.payoutAmount), 0) from Payout p
		where p.sellerId = :sellerId and p.status = :status and p.scheduledPayDate = :payDate
		""")
	long sumPayoutAmount(int sellerId, PayoutStatus status, LocalDate payDate);

	// 판매자 요약: 지급보류 중인 정산 내역 수. 한 정산 내역에는 활성 보류가 하나뿐이라 보류 건수와 같다
	long countBySellerIdAndStatus(int sellerId, PayoutStatus status);
}
