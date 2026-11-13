package com.bukang.order.out;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.common.shared.kafka.outbox.OutboxKafkaEventRepository;
import com.bukang.common.shared.kafka.outbox.OutboxStatus;
import com.bukang.order.domain.OrderOutboxKafkaEvent;

// 발행 작업이 쓰는 조회는 common OutboxKafkaEventRepository에 있다 (BUK-207)
public interface OrderOutboxKafkaEventRepository extends OutboxKafkaEventRepository<OrderOutboxKafkaEvent> {
	// 상태별 행을 저장 순서대로 limit건 읽는다. 잠그지 않는다 (발행 실패 목록 API, BUK-180)
	@Query("select e from OrderOutboxKafkaEvent e where e.status = :status order by e.id")
	List<OrderOutboxKafkaEvent> findListByStatus(@Param("status") OutboxStatus status, Limit limit);

	// 발행 실패 행만 보낼 차례로 되돌리고 재시도 횟수·다음 시도 시각을 비운다 (BUK-180, PR #436 리뷰). 바꾼 행 수를 돌려준다
	// 읽고 엔티티로 바꾸면 동시에 두 번 불렀을 때 둘 다 통과하고, 늦게 커밋한 쪽이 릴레이가 SENT로 바꾼 행을 PENDING으로 덮어 한 번 더 보낸다
	// 상태 조건을 UPDATE에 넣어 DB가 한쪽만 바꾸게 한다 (InnoDB는 먼저 바꾼 쪽이 커밋한 값으로 조건을 다시 본다)
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
		update OrderOutboxKafkaEvent e
		set e.status = com.bukang.common.shared.kafka.outbox.OutboxStatus.PENDING, e.retryCount = 0,
			e.nextRetryDate = null, e.modifyDate = :now
		where e.id = :id and e.status = com.bukang.common.shared.kafka.outbox.OutboxStatus.SEND_FAILED
		""")
	int resetSendFailed(@Param("id") long id, @Param("now") LocalDateTime now);
}
