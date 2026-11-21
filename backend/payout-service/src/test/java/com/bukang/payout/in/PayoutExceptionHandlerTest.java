package com.bukang.payout.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.bukang.common.global.rsdata.RsData;

/**
 * 정산 예외 처리기가 응답만 하지 않고 로그 한 줄을 남기는지 확인한다 (CRU-111)
 * 운영 로그는 앱이 찍은 줄만 모이므로, 찍지 않은 400 · 409는 Grafana에서 볼 수 없다
 */
@ExtendWith(OutputCaptureExtension.class)
class PayoutExceptionHandlerTest {
	private static final String PATH = "/api/v1/payout/holds/7";

	private final PayoutExceptionHandler handler = new PayoutExceptionHandler();
	private final MockHttpServletRequest request = new MockHttpServletRequest("POST", PATH);

	@Test
	@DisplayName("필수 헤더가 없으면 400과 함께 메서드 · 경로 · 코드 · 메시지를 info로 남긴다")
	void missingHeaderLogsInfo(CapturedOutput output) throws Exception {
		MissingRequestHeaderException exception = new MissingRequestHeaderException("Idempotency-Key", parameter());

		ResponseEntity<RsData<Void>> response = handler.missingRequestHeaderException(exception, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().message()).isEqualTo("필수 요청 헤더(Idempotency-Key)가 없습니다.");
		assertThat(line(output, "400 POST")).contains("INFO")
			.contains("400 POST " + PATH + " code=INVALID_REQUEST, message=필수 요청 헤더(Idempotency-Key)가 없습니다.");
	}

	@Test
	@DisplayName("파라미터 검증 실패는 400과 함께 검증 메시지를 info로 남긴다")
	void validationFailureLogsInfo(CapturedOutput output) {
		HandlerMethodValidationException exception = mock(HandlerMethodValidationException.class);
		doReturn(List.of(new DefaultMessageSourceResolvable(new String[] {"Positive"}, "정산 내역 ID는 1 이상이어야 합니다.")))
			.when(exception).getAllErrors();

		ResponseEntity<RsData<Void>> response = handler.handlerMethodValidationException(exception, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().message()).isEqualTo("정산 내역 ID는 1 이상이어야 합니다.");
		assertThat(line(output, "400 POST")).contains("INFO")
			.contains("code=INVALID_REQUEST, message=정산 내역 ID는 1 이상이어야 합니다.");
	}

	@Test
	@DisplayName("필수 쿼리 파라미터가 없으면 응답은 그대로 두고, 빠진 칸 이름을 info로 남긴다")
	void missingParameterLogsName(CapturedOutput output) {
		MissingServletRequestParameterException exception =
			new MissingServletRequestParameterException("raceDate", "LocalDate");

		ResponseEntity<RsData<Void>> response = handler.missingServletRequestParameterException(exception, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().message()).isEqualTo("필수 요청 값이 없습니다.");
		assertThat(line(output, "400 POST")).contains("INFO").contains("필수 요청 값 없음 name=raceDate");
	}

	@Test
	@DisplayName("형식 오류는 칸 이름만 info로 남기고, 보낸 값은 남기지 않는다")
	void typeMismatchLogsNameWithoutValue(CapturedOutput output) throws Exception {
		MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException("010-1234-5678",
			Long.class, "payoutId", parameter(), new NumberFormatException("For input string: \"010-1234-5678\""));

		ResponseEntity<RsData<Void>> response = handler.methodArgumentTypeMismatchException(exception, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().message()).isEqualTo("요청 값의 형식이 올바르지 않습니다.");
		assertThat(line(output, "400 POST")).contains("INFO").contains("요청 값 형식 오류 name=payoutId");
		assertThat(output.getOut()).doesNotContain("010-1234-5678");
	}

	@Test
	@DisplayName("낙관적 락 충돌은 409와 함께 메서드 · 경로만 warn으로 남기고, 예외 메시지와 스택은 남기지 않는다")
	void optimisticLockLogsWarnWithPathOnly(CapturedOutput output) {
		OptimisticLockingFailureException exception =
			new OptimisticLockingFailureException("Row was updated by another transaction: Payout#7");

		ResponseEntity<RsData<Void>> response = handler.optimisticLockingFailureException(exception, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody().message()).isEqualTo("다른 요청이 먼저 정산 내역을 바꿨습니다. 다시 시도해주세요.");
		assertThat(line(output, "409 POST")).contains("WARN").contains("409 POST " + PATH);
		assertThat(output.getOut()).doesNotContain("Row was updated")
			.doesNotContain("OptimisticLockingFailureException");
	}

	// 처리기마다 한 줄만 남는지도 함께 본다
	private static String line(CapturedOutput output, String marker) {
		List<String> lines = output.getOut().lines().filter(text -> text.contains(marker)).toList();
		assertThat(lines).hasSize(1);
		return lines.get(0);
	}

	private static MethodParameter parameter() throws NoSuchMethodException {
		return new MethodParameter(PayoutExceptionHandlerTest.class.getDeclaredMethod("sample", String.class), 0);
	}

	@SuppressWarnings("unused")
	private static void sample(String value) {
	}
}
