package com.bukang.common.shared.kafka.dlt;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 다시 보내기 API에서 Kafka 브로커에 메시지를 보내지 못했을 때. 행은 처리 실패로 되돌리고 503으로 응답한다
 * (브로커가 돌아오면 같은 API를 다시 부르면 된다)
 * 예: [product] DLT 메시지 15번을 Kafka로 다시 보내지 못했습니다. 잠시 뒤 다시 시도하세요.
 */
public class DltKafkaResendException extends BusinessException {
	public DltKafkaResendException(String service, long id, Throwable cause) {
		super(HttpStatus.SERVICE_UNAVAILABLE, "KAFKA_DLT_RESEND_FAILED",
			"[" + service + "] DLT 메시지 " + id + "번을 Kafka로 다시 보내지 못했습니다. 잠시 뒤 다시 시도하세요.");
		initCause(cause);
	}
}
