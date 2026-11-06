package com.bukang.order.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
		+ "p.status) from Party p where p.id = :id")
	Optional<ApplyTarget> findApplyTarget(@Param("id") long id);

	record ApplyTarget(long raceCourseId, long crewId, long leaderMemberId, PartyStatus status) {
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

	// 링크 미리 보기 (BUK-39). 다시 만든 옛 토큰은 찾지 못한다
	Optional<Party> findByJoinToken(String joinToken);

	// 크루 홈 「진행 중인 파티」 (BUK-39)
	// shortcut: crew_id·status 인덱스가 없다. 크루당 파티가 많아지면 (crew_id, status) 인덱스를 Flyway로 더한다
	List<Party> findByCrewIdAndStatusInOrderByIdDesc(long crewId, Collection<PartyStatus> statuses);
}
