package com.bukang.payout.in;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.bukang.common.global.rsdata.RsData;

/**
 * 정산 서비스 전용 요청 예외 처리
 * 처리하지 않으면 /error로 넘어가 공통 응답 형식(RsData)이 아닌 기본 에러 응답이 나간다.
 */
@RestControllerAdvice
public class PayoutExceptionHandler {
	// 입력값 오류 코드는 common GlobalExceptionHandler와 같은 값을 쓴다
	private static final String INVALID_REQUEST = "INVALID_REQUEST";

	// 필수 요청 헤더가 없으면 잘못된 요청이다. 회원 ID 헤더는 common의 @LoginMemberId가 처리한다
	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<RsData<Void>> missingRequestHeaderException(MissingRequestHeaderException exception) {
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(RsData.of(HttpStatus.BAD_REQUEST, INVALID_REQUEST,
				"필수 요청 헤더(%s)가 없습니다.".formatted(exception.getHeaderName()), null));
	}

	// 컨트롤러 파라미터에 직접 붙인 검증(예: 경로 변수 @Positive) 실패. 첫 번째 오류 메시지를 응답한다
	// 이런 검증이 있는 메서드는 @Valid 요청 본문 오류(예: 보류 사유 없음)도 이 예외로 오므로,
	// 공통 처리(MethodArgumentNotValidException)와 같은 코드를 붙인다
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<RsData<Void>> handlerMethodValidationException(HandlerMethodValidationException exception) {
		String message = exception.getAllErrors().stream()
			.findFirst()
			.map(MessageSourceResolvable::getDefaultMessage)
			.orElse("요청 값이 올바르지 않습니다.");
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(RsData.of(HttpStatus.BAD_REQUEST, INVALID_REQUEST, message, null));
	}

	// 필수 쿼리 파라미터 누락 (예: 환불 가능 여부 조회에 대회일 없음)
	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<RsData<Void>> missingServletRequestParameterException(
		MissingServletRequestParameterException exception
	) {
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(RsData.of(HttpStatus.BAD_REQUEST, "필수 요청 값이 없습니다.", null));
	}

	// 경로 변수·헤더·쿼리 파라미터의 형식 오류 (예: 정산 내역 ID에 문자, 대회일이 날짜 형식이 아님)
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<RsData<Void>> methodArgumentTypeMismatchException(
		MethodArgumentTypeMismatchException exception
	) {
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(RsData.of(HttpStatus.BAD_REQUEST, "요청 값의 형식이 올바르지 않습니다.", null));
	}

	// Payout의 @Version 충돌: 지급 배치나 다른 관리자 요청이 같은 정산 내역을 먼저 바꿨다
	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ResponseEntity<RsData<Void>> optimisticLockingFailureException(
		OptimisticLockingFailureException exception
	) {
		return ResponseEntity
			.status(HttpStatus.CONFLICT)
			.body(RsData.of(HttpStatus.CONFLICT, "다른 요청이 먼저 정산 내역을 바꿨습니다. 다시 시도해주세요.", null));
	}
}
