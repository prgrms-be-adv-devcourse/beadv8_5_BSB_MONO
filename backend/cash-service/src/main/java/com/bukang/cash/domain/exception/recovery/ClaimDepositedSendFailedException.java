package com.bukang.cash.domain.exception.recovery;

// "청구 입금됨" 메시지를 Kafka에 보냈으나 브로커가 받았다는 답을 받지 못함 (연결 실패, 응답 시간 초과, 거절)
// 회원 요청이 아니라 스케줄러에서만 나므로 HTTP 응답 코드가 필요 없다. 보낸 시각을 비워 두고 다음 실행에서 다시 보낸다
public class ClaimDepositedSendFailedException extends RuntimeException {
	public ClaimDepositedSendFailedException(String message, Throwable cause) {
		super(message, cause);
	}
}
