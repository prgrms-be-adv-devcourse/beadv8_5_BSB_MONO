package com.bukang.order.out;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.order.domain.OrderOutboxKafkaEvent;
import com.bukang.order.domain.OutboxStatus;

public interface OrderOutboxKafkaEventRepository extends JpaRepository<OrderOutboxKafkaEvent, Long> {
	// 상태별 이벤트를 만든 순서대로 limit건 읽는다 (만든 시각이 같으면 ID 순)
	List<OrderOutboxKafkaEvent> findByStatusOrderByCreateDateAscIdAsc(OutboxStatus status, Limit limit);

	// 발행 실패 행만 미처리로 되돌리고 재시도 횟수·다음 시도 시각을 비운다 (BUK-180, PR #436 리뷰). 바꾼 행 수를 돌려준다
	// 읽고 엔티티로 바꾸면 동시에 두 번 불렀을 때 둘 다 통과하고, 늦게 커밋한 쪽이 릴레이가 SENT로 바꾼 행을 PENDING으로 덮어 한 번 더 보낸다
	// 상태 조건을 UPDATE에 넣어 DB가 한쪽만 바꾸게 한다 (InnoDB는 먼저 바꾼 쪽이 커밋한 값으로 조건을 다시 본다)
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
		update OrderOutboxKafkaEvent e
		set e.status = com.bukang.order.domain.OutboxStatus.PENDING, e.retryCount = 0, e.nextRetryDate = null,
			e.modifyDate = :now
		where e.id = :id and e.status = com.bukang.order.domain.OutboxStatus.SEND_FAILED
		""")
	int resetSendFailed(@Param("id") long id, @Param("now") LocalDateTime now);
}
