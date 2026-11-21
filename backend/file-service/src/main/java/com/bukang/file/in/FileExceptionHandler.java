package com.bukang.file.in;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.bukang.common.global.rsdata.RsData;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 파일 서비스 전용 요청 예외 처리
 * 처리하지 않으면 /error로 넘어가 공통 응답 형식(RsData)이 아닌 기본 에러 응답이 나간다.
 */
@Slf4j
@RestControllerAdvice
public class FileExceptionHandler {
	// 입력값 오류 코드는 common GlobalExceptionHandler와 같은 값을 쓴다
	private static final String INVALID_REQUEST = "INVALID_REQUEST";

	// 필수 요청 헤더(예: 중복 요청 방지 키)가 없으면 잘못된 요청이다. 회원 ID 헤더는 common의 @LoginMemberId가 처리한다
	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<RsData<Void>> missingRequestHeaderException(MissingRequestHeaderException exception,
		HttpServletRequest request) {
		String message = "필수 요청 헤더(%s)가 없습니다.".formatted(exception.getHeaderName());
		logBadRequest(request, INVALID_REQUEST, message);
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(RsData.of(HttpStatus.BAD_REQUEST, INVALID_REQUEST, message, null));
	}

	// 컨트롤러 파라미터에 직접 붙인 검증(회원 ID @Positive) 실패. 첫 번째 오류 메시지를 응답한다
	// 이런 검증이 있는 메서드는 @Valid 요청 본문 오류도 이 예외로 오므로, 공통 처리(MethodArgumentNotValidException)와 같은 코드를 붙인다
	// 처리하지 않으면 UnexpectedExceptionResolver가 500으로 응답한다
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<RsData<Void>> handlerMethodValidationException(HandlerMethodValidationException exception,
		HttpServletRequest request) {
		String message = exception.getAllErrors().stream()
			.findFirst()
			.map(MessageSourceResolvable::getDefaultMessage)
			.orElse("요청 값이 올바르지 않습니다.");
		logBadRequest(request, INVALID_REQUEST, message);
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(RsData.of(HttpStatus.BAD_REQUEST, INVALID_REQUEST, message, null));
	}

	// 필수 쿼리 파라미터 누락 (예: 대상별 파일 조회의 refType, refId)
	// 응답에는 이름을 넣지 않지만, 로그에는 어떤 값이 빠졌는지 남긴다
	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<RsData<Void>> missingServletRequestParameterException(
		MissingServletRequestParameterException exception,
		HttpServletRequest request
	) {
		logBadRequest(request, null, "필수 요청 값 누락 name=" + exception.getParameterName());
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(RsData.of(HttpStatus.BAD_REQUEST, "필수 요청 값이 없습니다.", null));
	}

	// 경로 변수·헤더·쿼리 파라미터의 숫자 형식 오류 (예: 파일 ID에 문자)
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<RsData<Void>> methodArgumentTypeMismatchException(
		MethodArgumentTypeMismatchException exception,
		HttpServletRequest request
	) {
		// 보낸 값은 개인정보일 수 있어 남기지 않고 칸 이름만 남긴다
		logBadRequest(request, null, "요청 값 형식 오류 name=" + exception.getName());
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(RsData.of(HttpStatus.BAD_REQUEST, "요청 값의 형식이 올바르지 않습니다.", null));
	}

	// common GlobalExceptionHandler와 같은 형식으로 400 이유를 info로 남긴다 (응답에만 이유가 있으면 서버에서 원인을 찾을 수 없다)
	private static void logBadRequest(HttpServletRequest request, String code, String message) {
		log.info("400 {} {} code={}, message={}", request.getMethod(), request.getRequestURI(), code, message);
	}
}
