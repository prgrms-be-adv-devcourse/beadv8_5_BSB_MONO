package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 모든 받는 사람이 카카오 하루 한도(앱 전체·보낸 사람)에 걸림
public class KakaoMessageDailyLimitException extends BusinessException {
	public static final String CODE = "KAKAO_MESSAGE_DAILY_LIMIT";

	public KakaoMessageDailyLimitException() {
		super(HttpStatus.TOO_MANY_REQUESTS, CODE, "오늘은 카카오톡으로 더 보낼 수 없습니다.");
	}
}
