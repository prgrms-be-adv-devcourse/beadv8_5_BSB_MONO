package com.bukang.ai.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 모델이나 대화 기록(Redis)에서 답을 받지 못함 (키 없음, 연결 실패, 시간 초과, 빈 답)
public class AiModelUnavailableException extends BusinessException {
	public AiModelUnavailableException() {
		super(HttpStatus.SERVICE_UNAVAILABLE, "AI_MODEL_UNAVAILABLE",
			"지금은 답변을 만들 수 없습니다. 잠시 뒤 다시 시도해주세요.");
	}
}
