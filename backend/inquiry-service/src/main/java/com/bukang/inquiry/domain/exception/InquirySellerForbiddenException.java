package com.bukang.inquiry.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 승인된 판매자가 아닌 회원이 판매자 문의를 보내려 했다
public class InquirySellerForbiddenException extends BusinessException {
	public InquirySellerForbiddenException() {
		super(HttpStatus.FORBIDDEN, "INQUIRY_SELLER_FORBIDDEN", "승인된 판매자만 이용할 수 있습니다.");
	}
}
