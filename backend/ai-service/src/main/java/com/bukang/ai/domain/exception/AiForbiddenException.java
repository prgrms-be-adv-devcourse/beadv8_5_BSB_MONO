package com.bukang.ai.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.ai.domain.AiScreen;
import com.bukang.common.global.exception.BusinessException;

// 그 화면의 역할(승인된 판매자·관리자)이 없는 회원이 챗봇을 불렀다
public class AiForbiddenException extends BusinessException {
	public AiForbiddenException(AiScreen screen) {
		super(HttpStatus.FORBIDDEN, "AI_FORBIDDEN", screen.getLabel() + "를 이용할 수 있는 회원이 아닙니다.");
	}
}
