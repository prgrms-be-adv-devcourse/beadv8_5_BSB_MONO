package com.bukang.cash.domain.exception.recovery;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// PG(Toss페이먼츠)에 가상계좌 발급을 요청했으나 계좌를 받지 못함 (거절 응답, 5xx, 연결 실패, 빈 응답)
// 발급됐는지 모르는 경우도 나누지 않는다. 다시 요청할 때 주문번호로 먼저 조회해 이미 만든 계좌를 찾기 때문이다
public class VirtualAccountIssueFailedException extends BusinessException {
	public VirtualAccountIssueFailedException(String message) {
		super(HttpStatus.SERVICE_UNAVAILABLE, message);
	}
}
