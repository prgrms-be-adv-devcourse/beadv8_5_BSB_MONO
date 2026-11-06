package com.bukang.file.out;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.common.shared.kafka.dlt.DltKafkaEventStatus;
import com.bukang.file.domain.FileDltKafkaEvent;

public interface FileDltKafkaEventRepository extends JpaRepository<FileDltKafkaEvent, Long> {
	// 같은 메시지의 행을 찾는다. DLT로 다시 온 메시지인지, 다시 보낸 메시지가 처리됐는지 볼 때 쓴다
	// message_id 유니크 제약이 있어 많아야 1건이다
	Optional<FileDltKafkaEvent> findByMessageId(String messageId);

	// 상태별 행을 최근 것부터 limit건 읽는다 (내부 API 목록)
	List<FileDltKafkaEvent> findByStatusOrderByIdDesc(DltKafkaEventStatus status, Limit limit);
}
