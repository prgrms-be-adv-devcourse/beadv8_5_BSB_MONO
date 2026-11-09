package com.bukang.order.out;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.domain.Party;
import com.bukang.order.domain.PartyStatus;

import jakarta.persistence.LockModeType;

public interface PartyRepository extends JpaRepository<Party, Long> {
	// 참가 링크로 파티 ID만 찾는다. 잠그기 전에 엔티티를 읽지 않으려고 ID만 돌려준다 (uk_party_join_token)
	@Query("select p.id from Party p where p.joinToken = :joinToken")
	Optional<Long> findIdByJoinToken(@Param("joinToken") String joinToken);

	// 합류 때 잠그기 전에 확인할 종목·크루. 엔티티를 읽으면 뒤에서 잠가 읽을 때 옛 상태가 그대로 돌아오므로 값만 읽는다
	@Query("select new com.bukang.order.out.PartyRepository$JoinTarget(p.raceCourseId, p.crewId) from Party p "
		+ "where p.id = :id")
	Optional<JoinTarget> findJoinTarget(@Param("id") long id);

	record JoinTarget(long raceCourseId, long crewId) {
	}

	// 파티 신청 때 잠그기 전에 파티장·상태로 먼저 거른다. 상품·회원을 부르기 전에 빨리 거절하려는 것이고, 잠근 뒤 다시 본다
	@Query("select new com.bukang.order.out.PartyRepository$ApplyTarget(p.raceCourseId, p.crewId, p.leaderMemberId, "
		+ "p.status, p.crewHoldRequestedAt) from Party p where p.id = :id")
	Optional<ApplyTarget> findApplyTarget(@Param("id") long id);

	// crewHoldRequestedAt: 크루 자리를 요청해 결과를 모르는 중인지 (재시도 길을 열지 정한다)
	record ApplyTarget(long raceCourseId, long crewId, long leaderMemberId, PartyStatus status,
		LocalDateTime crewHoldRequestedAt) {
	}

	// 이 대회에서 그 회원이 나가지 않은 살아 있는 파티 (BUK-91). 한 대회 한 신청이라 보통 하나지만, 혹시 여럿이면 최근 것부터
	@Query("select p from Party p join PartyMember m on m.partyId = p.id where p.raceId = :raceId "
		+ "and m.memberId = :memberId and m.leftAt is null and p.status in :statuses order by p.id desc")
	List<Party> findJoinedByRaceIdAndMemberId(@Param("raceId") long raceId, @Param("memberId") long memberId,
		@Param("statuses") Collection<PartyStatus> statuses);

	// 합류·나가기·링크 다시 만들기·(다음 이슈) 신청은 파티 줄을 먼저 잠근다 (잠금 순서: 파티 → 참가 신청)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Party p where p.id = :id")
	Optional<Party> findByIdForUpdate(@Param("id") long id);

	// 결제 마감 알림 대상 (BUK-38): 결제 진행 중, 아직 안 보냄, 기한이 지금 ~ remindUntil(지금 + 3시간) 사이
	@Query("select p.id from Party p where p.status = :status and p.paymentRemindedAt is null "
		+ "and p.paymentDueAt > :now and p.paymentDueAt <= :remindUntil order by p.id")
	List<Long> findIdsToRemind(@Param("status") PartyStatus status, @Param("now") LocalDateTime now,
		@Param("remindUntil") LocalDateTime remindUntil);

	// 결제 기간을 끝낼 대상 (BUK-38): 결제 진행 중이고, 기한이 지났거나 결제 대기 신청서가 하나도 없다(모두 결제함)
	// shortcut: (status, payment_due_at) 인덱스가 없다. 결제 진행 중 파티는 대회마다 몇 개라 전체를 훑어도 된다. 늘면 Flyway로 더한다
	@Query("select p.id from Party p where p.status = :status and (p.paymentDueAt <= :now or not exists "
		+ "(select pa.id from Participation pa where pa.partyId = p.id and pa.status = :waiting)) order by p.id")
	List<Long> findIdsToFinishPayment(@Param("status") PartyStatus status, @Param("now") LocalDateTime now,
		@Param("waiting") ParticipationStatus waiting);

	// 대회 다음 날 종료할 후보의 종목 (BUK-126). 대회 일시는 상품에 있어 종목마다 한 번 묻는다
	@Query("select distinct p.raceCourseId from Party p where p.status = :status")
	List<Long> findRaceCourseIdsByStatus(@Param("status") PartyStatus status);

	@Query("select p.id from Party p where p.raceCourseId = :raceCourseId and p.status = :status order by p.id")
	List<Long> findIdsByRaceCourseIdAndStatus(@Param("raceCourseId") long raceCourseId,
		@Param("status") PartyStatus status);

	// 링크 미리 보기 (BUK-39). 다시 만든 옛 토큰은 찾지 못한다
	Optional<Party> findByJoinToken(String joinToken);

	// 크루 홈 「진행 중인 파티」 (BUK-39)
	// shortcut: crew_id·status 인덱스가 없다. 크루당 파티가 많아지면 (crew_id, status) 인덱스를 Flyway로 더한다
	List<Party> findByCrewIdAndStatusInOrderByIdDesc(long crewId, Collection<PartyStatus> statuses);

	// 크루 없애기 확인 (BUK-125). 막는 파티를 ID 순으로 돌려준다
	List<Party> findByCrewIdAndStatusInOrderByIdAsc(long crewId, Collection<PartyStatus> statuses);
}
