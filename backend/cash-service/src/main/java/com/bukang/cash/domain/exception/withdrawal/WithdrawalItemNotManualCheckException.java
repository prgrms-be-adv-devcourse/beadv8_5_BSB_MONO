package com.bukang.cash.domain.exception.withdrawal;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 수동 확인 필요가 아닌 몫은 사람이 정리할 수 없음. 재확인이 맞추는 중이거나 이미 끝난 몫을 두 번 반영하지 않게 한다
public class WithdrawalItemNotManualCheckException extends BusinessException {
	public WithdrawalItemNotManualCheckException(String message) {
		super(HttpStatus.CONFLICT, "WITHDRAWAL_ITEM_NOT_MANUAL_CHECK", message);
	}
}
