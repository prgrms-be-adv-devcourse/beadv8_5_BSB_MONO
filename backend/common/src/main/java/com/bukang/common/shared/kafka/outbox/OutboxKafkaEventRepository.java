package com.bukang.common.shared.kafka.outbox;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

/**
 * 아웃박스 저장소의 공통 조회 (BUK-200). 서비스 저장소는 이 인터페이스를 상속만 한다
 * #{#entityName}은 상속한 저장소의 엔티티 이름으로 바뀐다
 */
@NoRepositoryBean
public interface OutboxKafkaEventRepository<E extends BaseOutboxKafkaEvent> extends JpaRepository<E, Long> {
	// 상태별 행을 저장 순서(id)대로 limit건 읽고 행 락을 건다 (SELECT ... FOR UPDATE SKIP LOCKED)
	// 락 대기 시간 -2는 Hibernate의 "기다리지 않고 건너뛰기"다. 다른 서버가 락 건 행은 읽지 않아 두 번 보내지 않는다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
	List<E> findByStatusOrderByIdAsc(OutboxStatus status, Limit limit);

	// 대상(종류 + ID)마다 가장 앞(id가 가장 작은) 행의 id. 락 없이 읽어, 다른 서버가 락 건 행도 센다
	@Query("select min(e.id) from #{#entityName} e where e.status = :status and e.id <= :maxId"
		+ " group by e.refType, e.refId")
	List<Long> findFirstIdsPerTarget(@Param("status") OutboxStatus status, @Param("maxId") long maxId);

	@Query("select e.id from #{#entityName} e where e.status = :status and e.sentDate < :before order by e.id")
	List<Long> findIdsByStatusAndSentDateBefore(
		@Param("status") OutboxStatus status,
		@Param("before") LocalDateTime before,
		Limit limit
	);
}
