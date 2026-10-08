package com.bukang.jwt;

/**
 * 토큰 검증 실패. 만료(EXPIRED)는 클라이언트가 재발급을 시도할 수 있도록 다른 실패(INVALID)와 구분한다.
 */
public class InvalidTokenException extends RuntimeException {
	private final Reason reason;

	public InvalidTokenException(Reason reason, String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	public Reason getReason() {
		return reason;
	}

	public enum Reason {
		EXPIRED,
		INVALID
	}
}
