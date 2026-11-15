package com.bukang.ai.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 회원 서비스에 연결하지 못해 역할을 확인할 수 없음 (연결 실패, 응답 시간 초과, 5xx, 응답 형식 오류)
public class AiExternalServiceUnavailableException extends BusinessException {
	public AiExternalServiceUnavailableException() {
		super(HttpStatus.SERVICE_UNAVAILABLE, "AI_EXTERNAL_SERVICE_UNAVAILABLE",
			"회원 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.");
	}
}
