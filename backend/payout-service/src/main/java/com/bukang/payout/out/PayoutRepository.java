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

public interface PayoutRepository extends JpaRepository<Payout, Long> {
	// 최종 정산 생성: 이미 만든 정산 내역인지 확인한다. 유일 조건 (seller_id, race_id, payout_month, payout_stage)과 같은 키다
	boolean existsBySellerIdAndRaceIdAndPayoutMonthAndPayoutStage(long sellerId, long raceId, String payoutMonth,
		PayoutStage payoutStage);

	// 1차 정산 생성: 같은 판매자 × 대회의 1차 · 최종 정산이 이미 있는지 본다
	// 1차는 정산 월과 상관없이 하나만 만든다 (환불 마감일이 다음 달로 늦춰지면 정산 월이 바뀐다, PRO-232)
	// 최종이 먼저 생겼으면 1차를 만들지 않는다
	boolean existsBySellerIdAndRaceIdAndPayoutStage(long sellerId, long raceId, PayoutStage payoutStage);

	// 최종 정산 생성: 같은 판매자 × 대회의 1차 정산 내역 (1차 정산금 차감과 보류 여부를 읽는다)
	// 1차 정산은 정산 월과 상관없이 판매자 × 대회마다 하나만 만든다 (PRO-232)
	Optional<Payout> findFirstBySellerIdAndRaceIdAndPayoutStageOrderByIdAsc(long sellerId, long raceId,
		PayoutStage payoutStage);

	// 회수 상계: 같은 판매자 × 대회의 지급 전 정산 내역을 잠가서 읽는다 (만든 순서대로, 1차가 먼저)
	// 회수 요청 · 최종 생성 · 지급 배치가 같은 정산 내역 금액을 동시에 바꾸지 않게 한다 (PRO-214)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select p from Payout p
		where p.sellerId = :sellerId and p.raceId = :raceId and p.status in :statuses
		order by p.id
		""")
	List<Payout> findForUpdate(long sellerId, long raceId, Collection<PayoutStatus> statuses);

	// 대회 취소 처리: 그 대회의 정산 내역 전부 (지급 전은 취소로 닫고, 지급 완료는 회수액에 더한다)
	List<Payout> findByRaceId(long raceId);

	// 지급 배치: 지급 예정일이 오늘이거나 지난 정산 내역 (지난 날을 놓쳐도 다음 실행에서 따라잡는다)
	List<Payout> findByStatusAndScheduledPayDateLessThanEqual(PayoutStatus status, LocalDate date);

	// 지급 배치 대상 ID (PRO-252): 지급 예정일이 된 정산 예정 + 지난 실행에서 결과를 모른 채 남은 지급 중(날짜 무관)
	// 엔티티 대신 ID만 읽고, 한 건씩 새 트랜잭션에서 다시 읽어 그 사이 바뀐 상태(보류 등)를 보고 처리한다
	@Query("""
		select p.id from Payout p
		where (p.status = :calculated and p.scheduledPayDate <= :today) or p.status = :paying
		order by p.id
		""")
	List<Long> findPayTargetIds(LocalDate today, PayoutStatus calculated, PayoutStatus paying);

	// 판매자 정산 내역 조회: 정산 월(YYYY-MM)은 문자열이어도 자릿수가 같아 사전순 비교가 달 순서와 같다
	// 유일 조건 (seller_id, race_id, payout_month)의 인덱스는 seller_id로 시작하므로 판매자 범위를 이 인덱스로 찾는다
	List<Payout> findBySellerIdAndPayoutMonthBetweenOrderByPayoutMonthDescIdDesc(
		long sellerId, String fromMonth, String toMonth);

	// 판매자 요약: 지급 배치가 지급할 정산 예정 내역 (지급 예정일 순). 보류(HELD)는 상태가 달라 빠진다
	// 같은 판매자 × 대회의 살아 있는 1차가 아직 지급 완료가 아니면 그 최종은 지급 배치가 건너뛰므로 뺀다
	// (PRO-188, PayoutPayStepUseCase.findWaitingFirst와 같은 기준. 1차가 없거나 취소됐으면 기다리지 않는다)
	@Query("""
		select p from Payout p
		where p.sellerId = :sellerId and p.status = :calculated
		and not (p.payoutStage = :finalStage and exists (
			select f.id from Payout f
			where f.sellerId = p.sellerId and f.raceId = p.raceId and f.payoutStage = :firstStage
			and f.status <> :paid and f.status <> :canceled))
		order by p.scheduledPayDate, p.id
		""")
	List<Payout> findPayableForSummary(long sellerId, PayoutStatus calculated, PayoutStage finalStage,
		PayoutStage firstStage, PayoutStatus paid, PayoutStatus canceled);

	// 판매자 요약: 지급보류 중인 정산 내역 수. 한 정산 내역에는 활성 보류가 하나뿐이라 보류 건수와 같다
	long countBySellerIdAndStatus(long sellerId, PayoutStatus status);
}
