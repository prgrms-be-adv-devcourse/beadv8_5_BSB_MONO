package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

public class MemberReplicaPageInvalidException extends BusinessException {
	public MemberReplicaPageInvalidException(int maxSize) {
		super(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "afterId는 0 이상, size는 1~%d이어야 합니다.".formatted(maxSize));
	}
}
