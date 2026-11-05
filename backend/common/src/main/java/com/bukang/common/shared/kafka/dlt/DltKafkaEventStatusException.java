package com.bukang.common.shared.kafka.dlt;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 지금 상태에서 할 수 없는 일(이미 처리 완료인 행을 다시 보내기 등)을 요청했을 때. 내부 API가 409로 응답한다
 * 예: [product] DLT 메시지 15번은 처리 완료 상태라 다시 보낼 수 없습니다.
 */
public class DltKafkaEventStatusException extends BusinessException {
	public DltKafkaEventStatusException(String service, long id, DltKafkaEventStatus status, String action) {
		super(HttpStatus.CONFLICT, "KAFKA_DLT_INVALID_STATUS",
			"[" + service + "] DLT 메시지 " + id + "번은 " + status.getLabel() + " 상태라 " + action + " 수 없습니다.");
	}
}
