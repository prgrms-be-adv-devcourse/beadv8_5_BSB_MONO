package com.bukang.common.shared.kafka.dlt;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 내부 API로 다시 보내기·처리 완료를 요청한 DLT 행이 없을 때. 404로 응답한다
 * 예: [product] DLT 메시지 15번이 없습니다.
 */
public class DltKafkaEventNotFoundException extends BusinessException {
	public DltKafkaEventNotFoundException(String service, long id) {
		super(HttpStatus.NOT_FOUND, "KAFKA_DLT_NOT_FOUND", "[" + service + "] DLT 메시지 " + id + "번이 없습니다.");
	}
}
