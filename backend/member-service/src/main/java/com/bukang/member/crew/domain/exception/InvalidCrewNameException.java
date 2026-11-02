package com.bukang.member.crew.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 크루 이름이 앞뒤 공백을 뺀 1~20자가 아님. 요청 본문 검증과 같은 오류 코드를 쓴다
public class InvalidCrewNameException extends BusinessException {
	public InvalidCrewNameException() {
		super(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "크루 이름은 1자 이상 20자 이하로 입력해주세요.");
	}
}
