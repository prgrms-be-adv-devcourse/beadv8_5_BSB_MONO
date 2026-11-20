package com.bukang.member.recordcert.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 승인된 판매자가 아닌 회원이 판매자 기록증 심사 API를 부를 때 (BUK-276)
public class RecordCertNotSellerException extends BusinessException {
	public RecordCertNotSellerException() {
		super(HttpStatus.FORBIDDEN, "RECORD_CERT_NOT_SELLER", "승인된 판매자만 쓸 수 있습니다.");
	}
}
