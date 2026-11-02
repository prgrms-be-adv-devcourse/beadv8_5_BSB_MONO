package com.bukang.product.out;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.product.domain.RaceCourse;

/**
 * 종목의 확보·판매 카운터는 아래 조건부 UPDATE로만 바꾼다 (엔티티 저장에서는 빠진다, RaceCourse 카운터 주석)
 * 세미는 개인 예매만 해서 개인 몫 카운터만 바꾼다. 엔티티를 거치지 않으므로 수정 일시(updated_at)도 직접 채운다.
 */
public interface RaceCourseRepository extends JpaRepository<RaceCourse, Long> {
	// 종목의 대회 ID만 읽는다 (대회 엔티티를 불러오지 않는다)
	// 자리 확보가 대회 행을 잠그고 읽기 전에 대회를 먼저 불러 두면, 잠금 조회도 먼저 불러 둔 옛 상태를 돌려준다
	@Query("select c.race.id from RaceCourse c where c.id = :id")
	Optional<Long> findRaceIdById(@Param("id") long id);

	// 남은 개인 자리가 있을 때만 결제 대기를 1 늘린다. 마지막 자리를 두 사람이 함께 가져가지 못하게 하는 조건부 UPDATE다
	//   남은 자리를 먼저 읽고 늘리면, 마지막 1자리를 두 요청이 함께 '남았다'고 읽고 둘 다 늘린다
	//   이 UPDATE는 행을 잠근 뒤 조건을 다시 보므로, 두 번째 요청은 0행이 되어 매진으로 끝난다
	// 대회가 판매 예정·판매 중일 때만 늘린다. 판매자가 같은 순간 대회를 취소해도 취소된 대회에 자리가 잡히지 않게 한다
	//   (판매 예정도 넣는 이유: 판매 시작일 0시가 지나도 상태 배치가 판매 중으로 바꾸기까지 최대 5분 걸린다)
	@Modifying(clearAutomatically = true)
	@Query("update RaceCourse c set c.individualReserved = c.individualReserved + 1, c.modifyDate = :now "
		+ "where c.id = :id and c.individualQuota + c.movedQuota - c.individualReserved - c.individualSold > 0 "
		+ "and c.race.id in (select r.id from Race r where r.saleStatus in ("
		+ "com.bukang.common.shared.race.domain.RaceSaleStatus.SCHEDULED, "
		+ "com.bukang.common.shared.race.domain.RaceSaleStatus.ON_SALE))")
	int reserveIndividual(@Param("id") long id, @Param("now") LocalDateTime now);

	// 결제 대기 1 → 판매 1 (남은 자리는 그대로)
	@Modifying(clearAutomatically = true)
	@Query("update RaceCourse c set c.individualReserved = c.individualReserved - 1, "
		+ "c.individualSold = c.individualSold + 1, c.modifyDate = :now where c.id = :id")
	int sellIndividual(@Param("id") long id, @Param("now") LocalDateTime now);

	// 결제 대기 count개를 정원으로 되돌린다 (해제. 대회 취소는 한 종목의 여러 자리를 한 번에 되돌린다)
	@Modifying(clearAutomatically = true)
	@Query("update RaceCourse c set c.individualReserved = c.individualReserved - :count, c.modifyDate = :now "
		+ "where c.id = :id")
	int releaseIndividual(@Param("id") long id, @Param("count") int count, @Param("now") LocalDateTime now);

	// 판매 1을 정원으로 되돌린다 (결제 취소)
	@Modifying(clearAutomatically = true)
	@Query("update RaceCourse c set c.individualSold = c.individualSold - 1, c.modifyDate = :now where c.id = :id")
	int restoreIndividual(@Param("id") long id, @Param("now") LocalDateTime now);

	// 남은 개인 자리로 종목 상태를 맞춘다: 0이면 정원 마감, 남았으면 접수 중
	// 카운터를 바꾼 같은 트랜잭션에서 부른다 (행이 이미 잠겨 있어 그 사이 다른 요청이 끼어들지 못한다)
	// 세미는 개인 예매만 해서 개인 자리로만 판단한다
	@Modifying(clearAutomatically = true)
	@Query("update RaceCourse c set c.status = case "
		+ "when c.individualQuota + c.movedQuota - c.individualReserved - c.individualSold > 0 "
		+ "then com.bukang.product.domain.CourseStatus.OPEN "
		+ "else com.bukang.product.domain.CourseStatus.SOLD_OUT end "
		+ "where c.id = :id")
	int refreshStatus(@Param("id") long id);
}
