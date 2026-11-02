package com.bukang.queue.out;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.queue.domain.QueueRaceCourse;

import jakarta.persistence.LockModeType;

public interface QueueRaceCourseRepository extends JpaRepository<QueueRaceCourse, Long> {
	// 종목 복제본 행을 잠그고 읽는다 (SELECT … FOR UPDATE). 종목 동기화가 쓴다
	// 잠그는 이유는 QueueRaceRepository.findByIdForUpdate와 같다 (같은 종목의 메시지 두 건이 겹치면 차례로 처리)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from QueueRaceCourse c where c.id = :id")
	Optional<QueueRaceCourse> findByIdForUpdate(@Param("id") long id);
}
