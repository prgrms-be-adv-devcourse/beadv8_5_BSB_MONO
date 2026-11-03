package com.bukang.cash.domain.exception;

// PG에 결제 상태를 재조회했으나 조회 자체가 실패함 (연결 실패, 응답 시간 초과, 5xx)
// 승인 여부를 "확인했지만 아직 안 끝남"과 구분해야 해서, 빈 값이 아니라 예외로 알린다
public class PgLookupFailedException extends RuntimeException {
	public PgLookupFailedException(String message, Throwable cause) {
		super(message, cause);
	}
}
