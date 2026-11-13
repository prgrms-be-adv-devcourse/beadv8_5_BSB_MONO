package com.bukang.board.out;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.board.domain.BoardInboxKafkaEvent;

public interface BoardInboxKafkaEventRepository extends JpaRepository<BoardInboxKafkaEvent, Long> {
	// 처리한 메시지로 남긴다. 처음 받은 메시지면 1, 같은 그룹·메시지 ID가 이미 있으면 0을 돌려준다
	// 먼저 SELECT로 확인하면 동시에 온 같은 메시지가 둘 다 통과하므로, DB의 유니크 제약으로 한 번에 판단한다
	// 중복 키 예외를 내지 않아 트랜잭션이 롤백 전용으로 바뀌지 않는다. 네이티브 쿼리라 생성·수정 시각도 직접 넣는다
	@Modifying
	@Query(value = "insert ignore into board_inbox_kafka_event "
		+ "(message_id, consumer_group, topic, processed_date, create_date, modify_date) "
		+ "values (:messageId, :consumerGroup, :topic, :now, :now, :now)", nativeQuery = true)
	int insertIgnore(
		@Param("messageId") String messageId,
		@Param("consumerGroup") String consumerGroup,
		@Param("topic") String topic,
		@Param("now") LocalDateTime now
	);
}
