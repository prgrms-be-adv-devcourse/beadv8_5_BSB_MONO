package com.bukang.product.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.product.domain.RaceCourse;

import jakarta.persistence.LockModeType;

/**
 * 종목의 확보·판매 카운터는 아래 조건부 UPDATE로만 바꾼다 (엔티티 저장에서는 빠진다, RaceCourse 카운터 주석)
 * 개인 자리는 개인 몫 카운터를, 크루 자리(파티 신청)는 크루 몫 카운터를 바꾼다.
 * 엔티티를 거치지 않으므로 수정 일시(updated_at)도 직접 채운다.
 */
public interface RaceCourseRepository extends JpaRepository<RaceCourse, Long> {
	// 종목 행을 잠그고 읽는다 (SELECT … FOR UPDATE). 크루 자리는 파티원 수만큼 한꺼번에 잡으므로,
	// 여러 종목의 남은 자리를 모두 확인한 뒤에 카운터를 바꿔야 한다 (하나라도 모자라면 한 석도 잡지 않는다)
	// 잠그지 않고 읽으면 확인과 UPDATE 사이에 다른 파티가 자리를 가져가 일부 종목만 잡힐 수 있다
	// 여러 종목을 잠글 때는 종목 ID 순서로 부른다. 순서가 다르면 두 파티가 서로 상대가 잠근 종목을 기다리다 멈춘다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from RaceCourse c where c.id = :id")
	Optional<RaceCourse> findByIdForUpdate(@Param("id") long id);

	// 종목의 대회 ID만 읽는다 (대회 엔티티를 불러오지 않는다)
	// 자리 확보가 대회 행을 잠그고 읽기 전에 대회를 먼저 불러 두면, 잠금 조회도 먼저 불러 둔 옛 상태를 돌려준다
	@Query("select c.race.id from RaceCourse c where c.id = :id")
	Optional<Long> findRaceIdById(@Param("id") long id);

	// 남은 개인 자리가 있을 때만 결제 대기를 1 늘린다. 마지막 자리를 두 사람이 함께 가져가지 못하게 하는 조건부 UPDATE다
	//   남은 자리를 먼저 읽고 늘리면, 마지막 1자리를 두 요청이 함께 '남았다'고 읽고 둘 다 늘린다
	//   이 UPDATE는 행을 잠근 뒤 조건을 다시 보므로, 두 번째 요청은 0행이 되어 매진으로 끝난다
	// 대회가 판매 예정·판매 중일 때만 늘린다. 판매자가 같은 순간 대회를 취소해도 취소된 대회에 자리가 잡히지 않게 한다
	//   (판매 예정도 넣는 이유: 0시 정각 상태 배치가 늦거나 실패하면 다음 5분 정각까지 판매 예정으로 남을 수 있다)
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

	// 남은 크루 자리가 count개 이상일 때만 크루 결제 대기를 count 늘린다 (파티원 수만큼 한꺼번에)
	// 크루 몫을 개인 몫으로 옮기기 전(crewClosedAt이 빔)이고, 대회가 판매 예정·판매 중일 때만 늘린다
	// 확보는 종목 행을 먼저 잠그고 남은 자리를 확인한 뒤 부르므로 보통 1행이다. 0행이면 확인과 어긋난 것이다
	@Modifying(clearAutomatically = true)
	@Query("update RaceCourse c set c.crewReserved = c.crewReserved + :count, c.modifyDate = :now "
		+ "where c.id = :id and c.crewQuota - c.movedQuota - c.crewReserved - c.crewSold >= :count "
		+ "and c.crewClosedAt is null "
		+ "and c.race.id in (select r.id from Race r where r.saleStatus in ("
		+ "com.bukang.common.shared.race.domain.RaceSaleStatus.SCHEDULED, "
		+ "com.bukang.common.shared.race.domain.RaceSaleStatus.ON_SALE))")
	int reserveCrew(@Param("id") long id, @Param("count") int count, @Param("now") LocalDateTime now);

	// 크루 결제 대기 1 → 크루 판매 1 (남은 자리는 그대로)
	@Modifying(clearAutomatically = true)
	@Query("update RaceCourse c set c.crewReserved = c.crewReserved - 1, "
		+ "c.crewSold = c.crewSold + 1, c.modifyDate = :now where c.id = :id")
	int sellCrew(@Param("id") long id, @Param("now") LocalDateTime now);

	// 크루 결제 대기 count개를 되돌린다 (해제. 대회 취소는 한 종목의 여러 자리를 한 번에 되돌린다)
	// 크루 몫을 이미 개인 몫으로 옮긴 뒤(crewClosedAt이 있음)라면 되돌린 자리도 개인 몫으로 넘긴다 (movedQuota + count)
	//   크루 예매가 끝나 크루 몫에 남겨 두면 아무도 살 수 없는 자리가 된다
	//   남은 크루 자리 식(crewQuota − movedQuota − crewReserved − crewSold)은 그대로 0, 남은 개인 자리는 count 늘어난다
	// 보통은 이 경우가 생기지 않는다. 크루 마감일이 지난 자정 배치가 결제 대기 크루 자리를 먼저 모두 반환한 뒤(이때는 옮기기 전)
	// 남은 크루 자리를 한 번에 개인 몫으로 옮기기 때문이다. 배치와 늦게 온 해제가 겹칠 때를 위한 안전장치다
	@Modifying(clearAutomatically = true)
	@Query("update RaceCourse c set c.crewReserved = c.crewReserved - :count, "
		+ "c.movedQuota = c.movedQuota + case when c.crewClosedAt is null then 0 else :count end, "
		+ "c.modifyDate = :now where c.id = :id")
	int releaseCrew(@Param("id") long id, @Param("count") int count, @Param("now") LocalDateTime now);

	// 크루 판매 1을 되돌린다 (결제 취소). 크루 몫을 이미 개인 몫으로 옮긴 뒤라면 그 자리도 개인 몫으로 넘긴다 (releaseCrew와 같은 이유)
	// 파티원이 결제한 뒤 크루 마감이 지나 환불하면 생긴다. 자정 배치는 판매한 자리를 옮기지 않으므로, 환불된 자리는 여기서 넘긴다
	@Modifying(clearAutomatically = true)
	@Query("update RaceCourse c set c.crewSold = c.crewSold - 1, "
		+ "c.movedQuota = c.movedQuota + case when c.crewClosedAt is null then 0 else 1 end, "
		+ "c.modifyDate = :now where c.id = :id")
	int restoreCrew(@Param("id") long id, @Param("now") LocalDateTime now);

	// 크루 몫을 개인 몫으로 옮길 종목 (종목 ID 순): 크루 예매 마감일의 자정이 지났고 아직 옮기지 않은 종목
	// 크루 예매 마감 일시는 새 파티 참여 확정을 막는 기준이고, 결제 대기 크루 자리는 마감일 자정까지 결제할 수 있다
	//   예: 마감 11/12 18:00 → 18:00부터 파티 참여 확정은 막히고, 11/13 00:00 실행에서 결제 대기 반환과 개인 몫 이동
	//   마감이 11/13 00:00 정각이면 마지막 날은 11/12라 같은 11/13 00:00 실행에서 처리한다
	// todayStart = 오늘 0시. 「마감 일시 ≤ 오늘 0시」면 마감일의 자정이 이미 왔다
	// 크루 예매가 없는 대회(마감 일시 null)와 크루 정원이 0인 종목은 고르지 않는다
	@Query("select c.id from RaceCourse c where c.crewClosedAt is null and c.crewQuota > 0 "
		+ "and c.race.crewSaleEndAt <= :todayStart order by c.id")
	List<Long> findCrewClosableIds(@Param("todayStart") LocalDateTime todayStart);

	// 남은 크루 자리를 한 번에 개인 몫으로 옮기고 옮긴 일시를 남긴다
	// movedQuota = 지금 movedQuota + 남은 크루 자리 = crewQuota − crewReserved − crewSold
	// 결제 대기 크루 자리를 모두 반환한 뒤(crewReserved = 0)에만, 한 번만(crewClosedAt이 빔) 옮긴다
	//   반환 전에 옮기면 결제 대기 자리가 개인 몫 계산에서 빠지고, 반환할 때 releaseCrew가 그 자리를 또 개인 몫으로 넘긴다
	// 판매한 크루 자리(crewSold)는 옮기지 않는다. 나중에 환불되면 restoreCrew가 개인 몫으로 넘긴다
	@Modifying(clearAutomatically = true)
	@Query("update RaceCourse c set c.movedQuota = c.crewQuota - c.crewReserved - c.crewSold, "
		+ "c.crewClosedAt = :now, c.modifyDate = :now "
		+ "where c.id = :id and c.crewClosedAt is null and c.crewReserved = 0")
	int moveCrewQuotaToIndividual(@Param("id") long id, @Param("now") LocalDateTime now);

	// 남은 개인 자리로 종목 상태를 맞춘다: 0이면 정원 마감, 남았으면 접수 중
	// 카운터를 바꾼 같은 트랜잭션에서 부른다 (행이 이미 잠겨 있어 그 사이 다른 요청이 끼어들지 못한다)
	// 개인 자리로만 판단한다 (크루 자리를 해제·복구해 개인 몫으로 넘긴 뒤에도 부른다)
	@Modifying(clearAutomatically = true)
	@Query("update RaceCourse c set c.status = case "
		+ "when c.individualQuota + c.movedQuota - c.individualReserved - c.individualSold > 0 "
		+ "then com.bukang.product.domain.CourseStatus.OPEN "
		+ "else com.bukang.product.domain.CourseStatus.SOLD_OUT end "
		+ "where c.id = :id")
	int refreshStatus(@Param("id") long id);
}
