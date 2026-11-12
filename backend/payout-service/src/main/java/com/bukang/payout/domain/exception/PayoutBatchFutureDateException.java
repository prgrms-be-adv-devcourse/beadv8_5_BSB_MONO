package com.bukang.payout.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 관리자가 배치 기준일을 오늘보다 뒤로 줬다 (BUK-167 리뷰). 지급 배치는 지급 예정일 <= 기준일인 건을 고르므로
// 미래 날짜를 받으면 아직 지급일이 안 된 정산이 오늘 지급된다. 놓친 날을 따라잡는 과거 날짜는 받는다
public class PayoutBatchFutureDateException extends BusinessException {
	public PayoutBatchFutureDateException() {
		super(HttpStatus.BAD_REQUEST, "PAYOUT_BATCH_FUTURE_DATE", "배치 기준일은 오늘보다 뒤일 수 없습니다.");
	}
}
