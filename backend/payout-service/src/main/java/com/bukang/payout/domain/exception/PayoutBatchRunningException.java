package com.bukang.payout.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 같은 배치가 이미 돌고 있다 (BUK-167). 스케줄 · 관리자 실행이 같은 잠금을 쓴다
public class PayoutBatchRunningException extends BusinessException {
	public PayoutBatchRunningException(String batchName) {
		super(HttpStatus.CONFLICT, "PAYOUT_BATCH_RUNNING", batchName + " 배치가 이미 돌고 있습니다. 끝난 뒤 다시 돌려주세요.");
	}
}
