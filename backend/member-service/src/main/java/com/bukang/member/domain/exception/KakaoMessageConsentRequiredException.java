package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 저장한 카카오 토큰이 없거나, 동의 범위가 모자라거나, 카카오가 토큰을 받지 않음. 화면은 추가 동의로 안내한다
public class KakaoMessageConsentRequiredException extends BusinessException {
	public static final String CODE = "KAKAO_MESSAGE_CONSENT_REQUIRED";

	public KakaoMessageConsentRequiredException() {
		super(HttpStatus.FORBIDDEN, CODE, "카카오톡으로 보내려면 친구 목록과 메시지 보내기 동의가 필요합니다.");
	}
}
