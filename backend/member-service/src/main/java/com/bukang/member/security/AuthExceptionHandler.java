package com.bukang.member.security;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.bukang.common.global.rsdata.RsData;

/**
 * Spring Security 예외 처리 (회원 서비스 전용)
 * common의 GlobalExceptionHandler는 Security 의존성이 없는 서비스에서도 쓰이므로, Security 예외는 여기서 처리한다.
 */
@RestControllerAdvice
public class AuthExceptionHandler {
	// 로그인 실패: 이메일이 없는 경우와 비밀번호가 틀린 경우를 구분하지 않는다
	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<RsData<Void>> authenticationException(AuthenticationException exception) {
		return ResponseEntity
			.status(HttpStatus.UNAUTHORIZED)
			.body(RsData.of(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.", null));
	}
}
