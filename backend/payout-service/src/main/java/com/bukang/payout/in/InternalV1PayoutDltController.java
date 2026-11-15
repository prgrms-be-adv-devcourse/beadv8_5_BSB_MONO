package com.bukang.payout.in;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.kafka.dlt.DltKafkaEventDto;
import com.bukang.common.shared.kafka.dlt.DltKafkaEventStatus;
import com.bukang.payout.app.PayoutDltFacade;
import com.bukang.payout.dto.DltCompleteRequestDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/**
 * 정산(payout)이 끝내 처리하지 못한 Kafka 메시지(DLT 실패 테이블)를 보고, 다시 보내거나 처리 완료로 닫는 내부 API.
 * 게이트웨이에 라우팅하지 않는다(/internal/** 경로, Nginx도 404로 막는다). 사람이 Slack 알림을 받고 서버 안에서 curl로 부른다.
 * 예) docker compose exec payout-service curl -X POST localhost:8082/internal/v1/payout/kafka-failures/15/resend
 * 클래스에 @Validated를 붙이지 않는다. 붙이면 조회 건수 검사(@Min, @Max)가 Spring MVC가 아닌 AOP로 돌아
 * PayoutExceptionHandler가 받는 HandlerMethodValidationException 대신 ConstraintViolationException(500)이 난다.
 */
@Tag(name = "PayoutDlt", description = "Kafka 처리 실패 메시지 목록, 다시 보내기, 처리 완료 API (운영자 내부 호출 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/payout/kafka-failures")
public class InternalV1PayoutDltController {
	private final PayoutDltFacade payoutDltFacade;

	@GetMapping
	@Operation(summary = "처리 실패 메시지 목록",
		description = "상태별 DLT 실패 행을 최근 것부터 size건 돌려준다. 상태를 빼면 처리 실패(사람이 봐야 하는 메시지)를 보여 준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = PayoutDltApiExamples.LIST_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "없는 상태 값 또는 조회 건수 범위 밖",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "없는 상태 값", value = PayoutDltApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "조회 건수 범위 밖", value = PayoutDltApiExamples.LIST_SIZE_OUT_OF_RANGE)
			}))
	})
	public ResponseEntity<RsData<List<DltKafkaEventDto>>> list(
		@Parameter(description = "상태 (FAILED 처리 실패, RESENT 다시 보냄, COMPLETED 처리 완료)", example = "FAILED")
		@RequestParam(defaultValue = "FAILED") DltKafkaEventStatus status,
		@Parameter(description = "조회 건수 (1~100)", example = "50")
		@RequestParam(defaultValue = "50")
		@Min(value = 1, message = "조회 건수는 1 이상이어야 합니다.")
		@Max(value = 100, message = "한 번에 100건까지 조회할 수 있습니다.")
		int size
	) {
		List<DltKafkaEventDto> events = payoutDltFacade.findDlts(status, size);
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "처리 실패 메시지를 조회했습니다.", events));
	}

	@PostMapping("/{id}/resend")
	@Operation(summary = "처리 실패 메시지 다시 보내기",
		description = "처리 실패 상태의 메시지를 message-id 헤더째 정산의 첫 재시도 토픽으로 다시 보내고 다시 보냄으로 바꾼다. "
			+ "정산만 다시 받고(같은 토픽을 받는 다른 서비스는 받지 않음), 처리되면 처리 완료로, 또 실패하면 처리 실패로 돌아가 Slack으로 다시 알린다. "
			+ "Kafka로 보내지 못하면 처리 실패로 되돌리고 503으로 응답한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "다시 보냄",
			content = @Content(examples = @ExampleObject(value = PayoutDltApiExamples.RESEND_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutDltApiExamples.INVALID_VALUE_TYPE))),
		@ApiResponse(responseCode = "404", description = "없는 행",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutDltApiExamples.NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "처리 실패 상태가 아님 (이미 다시 보냈거나 처리 완료)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutDltApiExamples.RESEND_INVALID_STATUS))),
		@ApiResponse(responseCode = "503", description = "Kafka로 보내지 못함 (처리 실패로 되돌림)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutDltApiExamples.RESEND_FAILED)))
	})
	public ResponseEntity<RsData<DltKafkaEventDto>> resend(
		@Parameter(description = "DLT 실패 행 ID (Slack 알림의 행 ID)", example = "15")
		@PathVariable long id
	) {
		DltKafkaEventDto event = payoutDltFacade.resendDlt(id);
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "메시지를 다시 보냈습니다.", event));
	}

	@PostMapping("/{id}/complete")
	@Operation(summary = "처리 실패 메시지 처리 완료",
		description = "원인을 직접 고쳤거나 버릴 메시지를 메모와 함께 처리 완료로 닫는다. 처리 완료가 아니면(처리 실패, 다시 보냄) 닫을 수 있다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 완료",
			content = @Content(examples = @ExampleObject(value = PayoutDltApiExamples.COMPLETE_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "메모 없음, 메모가 500자 초과, 요청 본문 형식 오류 또는 ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "메모 없음", value = PayoutDltApiExamples.COMPLETE_MEMO_BLANK),
				@ExampleObject(name = "메모가 500자 초과", value = PayoutDltApiExamples.COMPLETE_MEMO_TOO_LONG),
				@ExampleObject(name = "요청 본문 형식 오류", value = PayoutDltApiExamples.INVALID_BODY),
				@ExampleObject(name = "ID 형식 오류", value = PayoutDltApiExamples.INVALID_VALUE_TYPE)
			})),
		@ApiResponse(responseCode = "404", description = "없는 행",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutDltApiExamples.NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "이미 처리 완료",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutDltApiExamples.COMPLETE_INVALID_STATUS)))
	})
	public ResponseEntity<RsData<DltKafkaEventDto>> complete(
		@Parameter(description = "DLT 실패 행 ID (Slack 알림의 행 ID)", example = "15")
		@PathVariable long id,
		@Valid @RequestBody DltCompleteRequestDto request
	) {
		DltKafkaEventDto event = payoutDltFacade.completeDlt(id, request.memo());
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "처리 완료로 바꿨습니다.", event));
	}
}
