package com.bukang.product.out;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.product.domain.HoldReleaseReason;
import com.bukang.product.domain.ParticipationHold;

import jakarta.persistence.LockModeType;

/**
 * 임시 확보한 자리. 상태는 조건부 UPDATE로만 바꾸고, 바뀐 행이 1개일 때만 종목 카운터를 바꾼다.
 * 엔티티를 거치지 않으므로 수정 일시(updated_at)도 직접 채운다.
 */
public interface ParticipationHoldRepository extends JpaRepository<ParticipationHold, Long> {
	Optional<ParticipationHold> findByParticipationId(long participationId);

	// 파티가 잡은 크루 자리 (같은 파티로 다시 확보를 부르면 처음 결과를 돌려주려고 찾는다)
	List<ParticipationHold> findByPartyIdOrderByParticipationId(long partyId);

	// 이 참가 신청들 중 이미 자리를 잡은 것이 있는지 (크루 자리를 잡기 전에, 다른 파티·개인 자리와 겹치는지 본다)
	boolean existsByParticipationIdIn(Collection<Long> participationIds);

	// 행을 잠그고 읽는다 (SELECT … FOR UPDATE). 같은 자리를 판매·해제·복구가 함께 처리하지 못하게 차례를 세운다
	// 잠그지 않고 읽으면, 다른 요청이 바꾸기 전의 상태를 보고 잘못 판단할 수 있다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select h from ParticipationHold h where h.participationId = :participationId")
	Optional<ParticipationHold> findForUpdate(@Param("participationId") long participationId);

	// 대회를 취소할 때 해제할 결제 대기 자리
	@Query("select h from ParticipationHold h where h.raceCourse.race.id = :raceId "
		+ "and h.status = com.bukang.product.domain.HoldStatus.HELD")
	List<ParticipationHold> findHeldByRaceId(@Param("raceId") long raceId);

	// 결제 기한이 지난 결제 대기 크루 자리 (매시간 배치가 반환한다)
	// 결제 기한 = min(파티 참여 확정 + 24시간, 크루 예매 마감일의 자정). 주문이 확보할 때 보낸다
	//   예: 16:30 확정 → 다음 날 16:30 기한 → 17:00 실행에서 반환
	//   크루 예매 마감이 11/12 18:00이면 11/12 15:00에 확정한 파티는 11/13 00:00이 기한이다 (그때 크루 몫을 개인 몫으로 옮긴다)
	// 판매(sell)는 expiresAt > now일 때만 되므로, expiresAt ≤ now인 자리는 더 팔 수 없는 자리다
	// 상태·기한 인덱스(idx_participation_hold_status_expires)로 찾는다
	@Query("select h from ParticipationHold h where h.status = com.bukang.product.domain.HoldStatus.HELD "
		+ "and h.expiresAt <= :now and h.partyId is not null")
	List<ParticipationHold> findExpiredHeldCrew(@Param("now") LocalDateTime now);

	// 한 종목의 결제 대기 크루 자리 (크루 마감일이 끝난 자정에 결제 기한과 상관없이 모두 반환한다)
	@Query("select h from ParticipationHold h where h.raceCourse.id = :raceCourseId "
		+ "and h.status = com.bukang.product.domain.HoldStatus.HELD and h.partyId is not null")
	List<ParticipationHold> findHeldCrewByRaceCourseId(@Param("raceCourseId") long raceCourseId);

	// 결제 대기 → 판매. 결제 기한(expires_at) 안일 때만 바꾼다
	@Modifying(clearAutomatically = true)
	@Query("update ParticipationHold h set h.status = com.bukang.product.domain.HoldStatus.SOLD, "
		+ "h.soldAt = :now, h.modifyDate = :now "
		+ "where h.id = :id and h.status = com.bukang.product.domain.HoldStatus.HELD and h.expiresAt > :now")
	int sell(@Param("id") long id, @Param("now") LocalDateTime now);

	// 결제 대기 → 해제 (활성 시간이 끝남, 대회 취소)
	@Modifying(clearAutomatically = true)
	@Query("update ParticipationHold h set h.status = com.bukang.product.domain.HoldStatus.RELEASED, "
		+ "h.releasedAt = :now, h.releaseReason = :reason, h.modifyDate = :now "
		+ "where h.id = :id and h.status = com.bukang.product.domain.HoldStatus.HELD")
	int release(@Param("id") long id, @Param("reason") HoldReleaseReason reason, @Param("now") LocalDateTime now);

	// 판매 → 환불 복구 (결제 취소)
	@Modifying(clearAutomatically = true)
	@Query("update ParticipationHold h set h.status = com.bukang.product.domain.HoldStatus.RESTORED, "
		+ "h.modifyDate = :now "
		+ "where h.id = :id and h.status = com.bukang.product.domain.HoldStatus.SOLD")
	int restore(@Param("id") long id, @Param("now") LocalDateTime now);
}
