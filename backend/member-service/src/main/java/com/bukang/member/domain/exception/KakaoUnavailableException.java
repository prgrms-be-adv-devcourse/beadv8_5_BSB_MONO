package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 카카오가 응답하지 않거나(2초) 서버 오류로 답함
public class KakaoUnavailableException extends BusinessException {
	public static final String CODE = "KAKAO_UNAVAILABLE";

	public KakaoUnavailableException() {
		super(HttpStatus.BAD_GATEWAY, CODE, "카카오톡이 응답하지 않습니다. 잠시 뒤 다시 시도해 주세요.");
	}
}
