package com.bukang.product.out;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.product.domain.ProductSeller;
import com.bukang.product.domain.Race;
import com.bukang.product.domain.RaceSaleStatus;

import jakarta.persistence.LockModeType;

// 잠그는 순서: 대회 행을 잡는 요청(수정·취소·자리 확보)은 모두 대회 행을 먼저, 종목 행을 나중에 잠근다
// 순서가 엇갈리면 MySQL에서 두 트랜잭션이 서로 상대의 잠금을 기다리다 한쪽이 교착으로 롤백된다
public interface RaceRepository extends JpaRepository<Race, Integer> {
	// 판매자의 대회 (최근 등록 순)
	List<Race> findAllBySellerOrderByIdDesc(ProductSeller seller);

	// 대회 행을 잠그고 읽는다 (SELECT … FOR UPDATE). 수정·취소가 쓴다
	// 같은 대회에서 겹치면 차례로 처리되어, 나중 요청이 최신 상태(예: 방금 취소됨)를 보고 막힌다
	// 잠그지 않고 읽으면 옛 상태로 판단해, 취소된 대회를 수정할 수 있다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Race r where r.id = :id")
	Optional<Race> findByIdForUpdate(@Param("id") int id);

	// 대회 행을 공유 잠금으로 읽는다 (MySQL SELECT … FOR SHARE). 자리 확보가 판매 상태를 볼 때 쓴다
	// 확보끼리는 서로 막지 않고, 취소·수정이 대회 행을 잡고 있으면 커밋을 기다렸다가 최신 상태를 본다
	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("select r from Race r where r.id = :id")
	Optional<Race> findByIdForShare(@Param("id") int id);

	// 상태 배치가 바꾼 대회를 다시 읽는다. 잠그고 읽어야 트랜잭션 처음의 스냅숏이 아닌 최신 상태를 본다
	// (그 사이 다른 요청이 취소한 대회를 옛 상태로 다시 색인하지 않게)
	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("select r from Race r where r.id in :ids")
	List<Race> findAllByIdForShare(@Param("ids") Collection<Integer> ids);

	// 정산 대상 대회 조회는 거르는 날짜 칸(환불 마감일 LocalDate / 대회 일시 LocalDateTime)과 경계 · 정렬이 달라 두 쿼리로 둔다
	// QueryDSL로 날짜 조건만 바꿔 끼우면 쿼리 2개치고 코드(커스텀 리포지토리)가 늘고,
	// JPQL 하나로 합치면 날짜 인자를 4개나 받아야 해서 지금처럼 둔다. 필요하면 그때 리팩토링한다 (PRO-230)

	// 정산 1차 대상: 환불 마감일이 [from, to](양 끝 포함)이고 판매 상태가 statuses 중 하나인 대회
	@Query("""
		select r from Race r
		where r.refundDeadline between :from and :to and r.saleStatus in :statuses
		order by r.refundDeadline, r.id
		""")
	List<Race> findPayoutTargetsByRefundDeadline(@Param("from") LocalDate from, @Param("to") LocalDate to,
		@Param("statuses") Collection<RaceSaleStatus> statuses);

	// 정산 최종 대상: 대회 일시가 [from, to)이고 판매 상태가 statuses 중 하나인 대회
	// 날짜 기간을 받아 끝 날의 다음 날 0시 미만으로 자른다
	@Query("""
		select r from Race r
		where r.raceDate >= :from and r.raceDate < :to and r.saleStatus in :statuses
		order by r.raceDate, r.id
		""")
	List<Race> findPayoutTargetsByRaceDate(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
		@Param("statuses") Collection<RaceSaleStatus> statuses);

	// 판매 상태 배치가 바꿀 대회 (판매 예정인데 판매 시작일이 된 대회)
	@Query("select r.id from Race r where r.saleStatus = :status and r.saleOpenDate <= :today")
	List<Integer> findIdsToOpen(@Param("status") RaceSaleStatus status, @Param("today") LocalDate today);

	// 판매 상태 배치가 바꿀 대회 (대회일이 지난 판매 예정·판매 중 대회)
	@Query("select r.id from Race r where r.saleStatus in :statuses and r.raceDate < :before")
	List<Integer> findIdsToClose(@Param("statuses") Collection<RaceSaleStatus> statuses,
		@Param("before") LocalDateTime before);

	// 아래 두 UPDATE는 ID를 읽을 때의 조건을 UPDATE에서 한 번 더 확인한다 (조건부 UPDATE)
	// ID를 읽은 뒤 판매자가 대회일·판매 시작일을 미뤘거나 상태를 바꿨으면 그 대회는 건너뛴다
	// 엔티티를 거치지 않으므로 수정 일시(updated_at)도 직접 채운다

	// 대회일이 지난 판매 예정·판매 중 대회 → 판매 종료
	@Modifying(clearAutomatically = true)
	@Query("update Race r set r.saleStatus = com.bukang.product.domain.RaceSaleStatus.CLOSED, r.modifyDate = :now "
		+ "where r.id in :ids and r.saleStatus in :statuses and r.raceDate < :before")
	int closeSale(@Param("ids") Collection<Integer> ids, @Param("statuses") Collection<RaceSaleStatus> statuses,
		@Param("before") LocalDateTime before, @Param("now") LocalDateTime now);

	// 판매자가 대회를 취소한다. 판매 종료 전 상태이고 대회일 다음 날 0시 전일 때만 바꾼다 (조건부 UPDATE)
	// 엔티티를 고쳐 커밋 때 저장하지 않는 이유: 확인하고 저장하는 사이 상태 배치가 판매 종료로 바꾸면 그 상태를 덮어쓴다
	//   조건부 UPDATE는 그 사이 상태가 바뀌었으면 0행이 되어 취소하지 않는다
	@Modifying(clearAutomatically = true)
	@Query("update Race r set r.saleStatus = com.bukang.product.domain.RaceSaleStatus.CANCELED, "
		+ "r.canceledAt = :now, r.modifyDate = :now "
		+ "where r.id = :id and r.saleStatus in :statuses and r.raceDate >= :today")
	int cancel(@Param("id") int id, @Param("statuses") Collection<RaceSaleStatus> statuses,
		@Param("today") LocalDateTime today, @Param("now") LocalDateTime now);

	// 판매 시작일이 된 판매 예정 대회 → 판매 중
	@Modifying(clearAutomatically = true)
	@Query("update Race r set r.saleStatus = com.bukang.product.domain.RaceSaleStatus.ON_SALE, r.modifyDate = :now "
		+ "where r.id in :ids and r.saleStatus = com.bukang.product.domain.RaceSaleStatus.SCHEDULED "
		+ "and r.saleOpenDate <= :today")
	int openSale(@Param("ids") Collection<Integer> ids, @Param("today") LocalDate today,
		@Param("now") LocalDateTime now);
}
