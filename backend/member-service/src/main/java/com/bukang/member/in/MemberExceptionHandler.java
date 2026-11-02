package com.bukang.member.in;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.bukang.common.global.rsdata.RsData;

/**
 * 회원 서비스 전용 요청 예외 처리
 * 처리하지 않으면 /error로 넘어가 공통 응답 형식(RsData)이 아닌 기본 에러 응답이 나간다.
 * 나중에 GlobalExceptionHandler에 포괄 핸들러가 생겨도 여기서 정한 응답이 먼저 쓰이도록 순서를 가장 앞에 둔다
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class MemberExceptionHandler {
	// 필수 요청 헤더가 없으면 잘못된 요청이다. 회원 ID 헤더는 common의 @LoginMemberId가 처리한다
	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<RsData<Void>> missingRequestHeaderException(MissingRequestHeaderException exception) {
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(RsData.of(HttpStatus.BAD_REQUEST, "필수 요청 헤더(%s)가 없습니다.".formatted(exception.getHeaderName()), null));
	}
}
