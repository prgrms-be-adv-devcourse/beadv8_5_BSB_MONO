package com.bukang.member.out;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.bukang.member.domain.MemberOutboxEvent;
import com.bukang.member.domain.MemberOutboxStatus;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface MemberOutboxEventRepository extends JpaRepository<MemberOutboxEvent, Long> {
	// 상태별 행을 저장 순서(id)대로 limit건 읽고 행 락을 건다 (SELECT ... FOR UPDATE SKIP LOCKED)
	// 락 대기 시간 -2는 Hibernate의 "기다리지 않고 건너뛰기"다. 다른 서버가 락 건 행은 읽지 않아 두 번 보내지 않는다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
	List<MemberOutboxEvent> findByStatusOrderByIdAsc(MemberOutboxStatus status, Limit limit);

	@Query("select e.id from MemberOutboxEvent e where e.status = :status and e.sentDate < :before order by e.id")
	List<Long> findIdsByStatusAndSentDateBefore(
		@Param("status") MemberOutboxStatus status,
		@Param("before") LocalDateTime before,
		Limit limit
	);
}
