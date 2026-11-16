package com.bukang.ai.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 다른 서비스에 연결하지 못함 (연결 실패, 응답 시간 초과, 5xx, 응답 형식 오류). 기본은 역할을 묻는 회원 서비스
public class AiExternalServiceUnavailableException extends BusinessException {
	public AiExternalServiceUnavailableException() {
		this("회원");
	}

	public AiExternalServiceUnavailableException(String serviceName) {
		super(HttpStatus.SERVICE_UNAVAILABLE, "AI_EXTERNAL_SERVICE_UNAVAILABLE",
			serviceName + " 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.");
	}
}
