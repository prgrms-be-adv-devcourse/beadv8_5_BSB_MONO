package com.bukang.product.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 없는 검색 문서 동기화 행을 재처리하려 할 때 (BUK-162)
public class RaceSyncEventNotFoundException extends BusinessException {
	public static final String CODE = "RACE_SYNC_EVENT_NOT_FOUND";

	public RaceSyncEventNotFoundException(long id) {
		super(HttpStatus.NOT_FOUND, CODE, "동기화 행 %d번이 없습니다.".formatted(id));
	}
}
