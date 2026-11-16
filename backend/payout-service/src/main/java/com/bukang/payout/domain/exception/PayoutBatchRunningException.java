package com.bukang.payout.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 같은 배치가 이미 돌고 있다 (BUK-167). 스케줄 · 관리자 실행이 같은 잠금을 쓴다
// 끝난 뒤에도 잠금을 30초 쥐므로 막 끝난 때도 이 예외가 난다. 그래서 문구에 "막 끝났습니다"를 넣는다 (PR #431 리뷰)
public class PayoutBatchRunningException extends BusinessException {
	public PayoutBatchRunningException(String batchName) {
		super(HttpStatus.CONFLICT, "PAYOUT_BATCH_RUNNING",
			batchName + " 배치가 이미 돌고 있거나 막 끝났습니다. 잠시 뒤 다시 돌려주세요.");
	}
}
