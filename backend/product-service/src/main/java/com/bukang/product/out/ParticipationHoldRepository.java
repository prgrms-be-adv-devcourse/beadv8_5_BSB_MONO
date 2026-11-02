package com.bukang.product.out;

import java.time.LocalDateTime;
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

	// 행을 잠그고 읽는다 (SELECT … FOR UPDATE). 같은 자리를 판매·해제·복구가 함께 처리하지 못하게 차례를 세운다
	// 잠그지 않고 읽으면, 다른 요청이 바꾸기 전의 상태를 보고 잘못 판단할 수 있다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select h from ParticipationHold h where h.participationId = :participationId")
	Optional<ParticipationHold> findForUpdate(@Param("participationId") long participationId);

	// 대회를 취소할 때 해제할 결제 대기 자리
	@Query("select h from ParticipationHold h where h.raceCourse.race.id = :raceId "
		+ "and h.status = com.bukang.product.domain.HoldStatus.HELD")
	List<ParticipationHold> findHeldByRaceId(@Param("raceId") long raceId);

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
