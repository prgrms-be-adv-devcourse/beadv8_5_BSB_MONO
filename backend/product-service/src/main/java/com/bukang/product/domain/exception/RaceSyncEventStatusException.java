package com.bukang.product.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;
import com.bukang.product.domain.SyncStatus;

// 처리 실패(FAILED)가 아닌 검색 문서 동기화 행을 재처리하거나 닫으려 할 때 (BUK-162)
public class RaceSyncEventStatusException extends BusinessException {
	public static final String CODE = "RACE_SYNC_EVENT_INVALID_STATUS";

	// action: "재처리할", "처리 완료로 닫을"
	public RaceSyncEventStatusException(Long id, SyncStatus status, String action) {
		super(HttpStatus.CONFLICT, CODE,
			"동기화 행 %d번은 %s 상태라 %s 수 없습니다. 처리 실패 상태에서만 할 수 있습니다."
				.formatted(id, status.getLabel(), action));
	}
}
