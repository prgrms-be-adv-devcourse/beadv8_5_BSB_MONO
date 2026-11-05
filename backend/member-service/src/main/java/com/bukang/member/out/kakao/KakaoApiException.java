package com.bukang.member.out.kakao;

import lombok.Getter;

/**
 * 카카오가 오류 코드로 답했다. 코드는 음수다 (-401 토큰 오류, -402 동의 없음, -10 앱 한도, -532 보낸 사람 하루 한도)
 * 부르는 쪽이 코드를 보고 다시 동의·한도 초과·장애 중 하나로 바꾼다
 */
@Getter
public class KakaoApiException extends RuntimeException {
	public static final int INVALID_TOKEN = -401;
	public static final int INSUFFICIENT_SCOPES = -402;
	public static final int APP_LIMIT_EXCEEDED = -10;
	public static final int SENDER_DAILY_LIMIT_EXCEEDED = -532;

	private final int code;

	public KakaoApiException(int code) {
		super("카카오 API 오류 code=" + code);
		this.code = code;
	}

	public boolean isTokenProblem() {
		return code == INVALID_TOKEN || code == INSUFFICIENT_SCOPES;
	}

	public boolean isDailyLimit() {
		return code == APP_LIMIT_EXCEEDED || code == SENDER_DAILY_LIMIT_EXCEEDED;
	}
}
