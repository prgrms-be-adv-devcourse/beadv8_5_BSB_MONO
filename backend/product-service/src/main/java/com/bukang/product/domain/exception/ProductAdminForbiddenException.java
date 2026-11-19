package com.bukang.product.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 관리자가 아닌 회원이 관리자 주소를 불렀다 (CRU-44)
public class ProductAdminForbiddenException extends BusinessException {
	public ProductAdminForbiddenException() {
		super(HttpStatus.FORBIDDEN, "PRODUCT_ADMIN_FORBIDDEN", "관리자만 이용할 수 있습니다.");
	}
}
