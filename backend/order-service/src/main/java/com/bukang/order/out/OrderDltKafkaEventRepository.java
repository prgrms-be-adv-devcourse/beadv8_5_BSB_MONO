package com.bukang.order.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.common.shared.kafka.dlt.DltKafkaEventStatus;
import com.bukang.order.domain.OrderDltKafkaEvent;

public interface OrderDltKafkaEventRepository extends JpaRepository<OrderDltKafkaEvent, Long> {
	// 같은 메시지의 행을 찾는다. message_id 유니크 제약이 있어 많아야 1건이다
	Optional<OrderDltKafkaEvent> findByMessageId(String messageId);

	// 상태별 행을 최근 것부터 limit건 읽는다 (내부 API 목록)
	List<OrderDltKafkaEvent> findByStatusOrderByIdDesc(DltKafkaEventStatus status, Limit limit);

	// 처리 실패 행만 다시 보냄으로 바꾼다 (PR #436 리뷰). 바꾼 행 수를 돌려준다
	// 엔티티의 markResent는 상태를 읽고 바꿔, 다시 보내기를 동시에 두 번 누르면 둘 다 통과해 같은 메시지가 두 번 나간다
	// 상태 조건을 UPDATE에 넣어 DB가 한쪽만 바꾸게 한다
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
		update OrderDltKafkaEvent e
		set e.status = com.bukang.common.shared.kafka.dlt.DltKafkaEventStatus.RESENT, e.resentDate = :now,
			e.modifyDate = :now
		where e.id = :id and e.status = com.bukang.common.shared.kafka.dlt.DltKafkaEventStatus.FAILED
		""")
	int markResent(@Param("id") long id, @Param("now") LocalDateTime now);

	// 다시 보냄으로 바꿨지만 보내지 못했을 때 처리 실패로 되돌린다. 그사이 처리 완료가 됐으면 덮어쓰지 않는다
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
		update OrderDltKafkaEvent e
		set e.status = com.bukang.common.shared.kafka.dlt.DltKafkaEventStatus.FAILED, e.modifyDate = :now
		where e.id = :id and e.status = com.bukang.common.shared.kafka.dlt.DltKafkaEventStatus.RESENT
		""")
	int cancelResent(@Param("id") long id, @Param("now") LocalDateTime now);
}
