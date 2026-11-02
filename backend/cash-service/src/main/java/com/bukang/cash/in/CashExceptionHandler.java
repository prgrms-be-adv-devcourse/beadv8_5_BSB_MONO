package com.bukang.cash.in;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.bukang.cash.domain.exception.PayoutTransferMismatchException;
import com.bukang.cash.dto.CashPayoutResponseDto;
import com.bukang.common.global.rsdata.RsData;

/**
 * 예치금 서비스 전용 요청 예외 처리 (product-service의 ProductExceptionHandler와 같은 방식)
 * 처리하지 않으면 Spring 기본 처리로 넘어가 공통 응답 형식(RsData)이 아닌 기본 에러 응답이 나간다.
 */
@RestControllerAdvice
public class CashExceptionHandler {
	// 입력값 오류 코드는 common GlobalExceptionHandler와 같은 값을 쓴다
	private static final String INVALID_REQUEST = "INVALID_REQUEST";

	// 필수 요청 헤더(예: 중복 요청 방지 키)가 없으면 잘못된 요청이다. 회원 ID 헤더는 common의 @LoginMemberId가 처리한다
	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<RsData<Void>> missingRequestHeaderException(MissingRequestHeaderException exception) {
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(RsData.of(HttpStatus.BAD_REQUEST, INVALID_REQUEST,
				"필수 요청 헤더(%s)가 없습니다.".formatted(exception.getHeaderName()), null));
	}

	// 컨트롤러 파라미터에 직접 붙인 검증(예: 경로 변수 @Positive) 실패. 첫 번째 오류 메시지를 응답한다
	// 이런 검증이 있는 메서드는 @Valid 요청 본문 오류도 이 예외로 오므로, 공통 처리(MethodArgumentNotValidException)와 같은 코드를 붙인다
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

	// 경로 변수·헤더의 숫자 형식 오류 (예: 회원 ID에 문자)
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<RsData<Void>> methodArgumentTypeMismatchException(
		MethodArgumentTypeMismatchException exception
	) {
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(RsData.of(HttpStatus.BAD_REQUEST, INVALID_REQUEST, "요청 값의 형식이 올바르지 않습니다.", null));
	}

	// 같은 정산 ID로 이전과 다른 값이 재요청되면, 지갑은 건드리지 않고 저장된 값을 데이터에 담아 409로 응답한다 (PRO-256 리뷰 반영)
	// GlobalExceptionHandler의 BusinessException 처리보다 이 예외 타입이 더 구체적이라 이쪽이 먼저 적용된다
	@ExceptionHandler(PayoutTransferMismatchException.class)
	public ResponseEntity<RsData<CashPayoutResponseDto>> payoutTransferMismatchException(
		PayoutTransferMismatchException exception
	) {
		CashPayoutResponseDto stored = CashPayoutResponseDto.alreadyPaid(
			exception.getStoredSettlementAmount(), exception.getStoredFeeAmount());
		return ResponseEntity
			.status(HttpStatus.CONFLICT)
			.body(RsData.of(HttpStatus.CONFLICT, exception.getMessage(), stored));
	}
}
