package com.bukang.order.out;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.order.domain.RaceScheduleNotice;

import jakarta.persistence.LockModeType;

public interface RaceScheduleNoticeRepository extends JpaRepository<RaceScheduleNotice, Long> {
	// 같은 대회 메시지를 두 곳(본 토픽·재시도 토픽)에서 함께 처리해도 한 번만 알리게 잠근다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select n from RaceScheduleNotice n where n.raceId = :raceId")
	Optional<RaceScheduleNotice> findByRaceIdForUpdate(@Param("raceId") long raceId);
}
