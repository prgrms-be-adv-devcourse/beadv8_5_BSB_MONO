package com.bukang.inquiry.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 없는 주문이거나, 내 주문이 아니거나, 주문에 그 대회가 없다. 남의 주문이 있는지도 알려 주지 않도록 모두 404로 답한다
public class InquiryOrderNotFoundException extends BusinessException {
	public InquiryOrderNotFoundException() {
		super(HttpStatus.NOT_FOUND, "INQUIRY_ORDER_NOT_FOUND", "주문을 찾을 수 없습니다.");
	}
}
