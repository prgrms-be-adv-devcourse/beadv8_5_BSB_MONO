package com.bukang.product.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 없는 대회를 조회·수정·취소하려 할 때
 * 오류 코드(RACE_NOT_FOUND)는 문의 서비스가 판매자 조회 내부 API에서 「대회 없음」을 알아보는 데 쓴다 (BUK-159).
 * 주소를 잘못 불러도 404가 나므로, 상태 코드만으로는 대회가 없는 것인지 구분할 수 없다
 */
public class RaceNotFoundException extends BusinessException {
	public static final String CODE = "RACE_NOT_FOUND";

	public RaceNotFoundException() {
		super(HttpStatus.NOT_FOUND, CODE, "존재하지 않는 대회입니다.");
	}
}
