package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 카카오 계정이 연결되지 않은 회원. 카카오톡으로 보내려면 계정 연결이 먼저다
public class KakaoNotLinkedException extends BusinessException {
	public static final String CODE = "KAKAO_NOT_LINKED";

	public KakaoNotLinkedException() {
		super(HttpStatus.CONFLICT, CODE, "카카오 계정을 먼저 연결해 주세요.");
	}
}
