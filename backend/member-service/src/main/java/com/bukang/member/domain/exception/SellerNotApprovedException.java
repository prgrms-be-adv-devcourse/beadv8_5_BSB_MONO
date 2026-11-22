package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 승인된 판매자만 정산 계좌를 보거나 바꿀 수 있다 (CRU-80). 심사 중이거나 반려된 판매자는 403이다
 */
public class SellerNotApprovedException extends BusinessException {
	public SellerNotApprovedException() {
		super(HttpStatus.FORBIDDEN, "SELLER_NOT_APPROVED", "승인된 판매자만 정산 계좌를 이용할 수 있습니다.");
	}
}
