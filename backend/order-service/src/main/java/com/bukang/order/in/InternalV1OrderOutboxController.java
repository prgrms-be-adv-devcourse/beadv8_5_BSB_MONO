package com.bukang.order.in;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.order.app.OrderKafkaFailureFacade;
import com.bukang.order.dto.OutboxFailureDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/**
 * 끝내 보내지 못한(발행 실패) 아웃박스 메시지를 보고 다시 보내게 되돌리는 내부 API (BUK-180).
 * 자리 복구·해제 메시지가 발행 실패로 남으면 정원이 돌아오지 않으므로, 원인(브로커 등)을 고친 뒤 되돌린다.
 * 게이트웨이에 라우팅하지 않는다(/internal/** 경로, Nginx도 404로 막는다). 사람이 Slack 알림을 받고 서버 안에서 curl로 부른다.
 * 예) docker compose exec order-service curl -X POST localhost:8084/internal/v1/order/outbox-failures/42/retry
 * 클래스에 @Validated를 붙이지 않는다 (InternalV1OrderDltController와 같은 이유).
 */
@Tag(name = "OrderOutbox", description = "발행 실패 아웃박스 메시지 목록, 다시 보내기 API (운영자 내부 호출 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/order/outbox-failures")
public class InternalV1OrderOutboxController {
	private final OrderKafkaFailureFacade orderKafkaFailureFacade;

	@GetMapping
	@Operation(summary = "발행 실패 메시지 목록", description = "발행 실패 상태의 아웃박스 행을 만든 순서대로 size건 돌려준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = OrderOutboxApiExamples.LIST_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "조회 건수 범위 밖 또는 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "조회 건수 범위 밖", value = OrderOutboxApiExamples.LIST_SIZE_OUT_OF_RANGE),
				@ExampleObject(name = "형식 오류", value = OrderOutboxApiExamples.INVALID_VALUE_TYPE)
			}))
	})
	public ResponseEntity<RsData<List<OutboxFailureDto>>> list(
		@Parameter(description = "조회 건수 (1~100)", example = "50")
		@RequestParam(defaultValue = "50")
		@Min(value = 1, message = "조회 건수는 1 이상이어야 합니다.")
		@Max(value = 100, message = "한 번에 100건까지 조회할 수 있습니다.")
		int size
	) {
		List<OutboxFailureDto> events = orderKafkaFailureFacade.findOutboxFailures(size);
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "발행 실패 메시지를 조회했습니다.", events));
	}

	@PostMapping("/{id}/retry")
	@Operation(summary = "발행 실패 메시지 다시 보내기",
		description = "발행 실패 행을 미처리(PENDING)로 되돌리고 재시도 횟수와 다음 시도 시각을 비운다. 아웃박스 릴레이가 1초 안에 다시 보낸다. "
			+ "그사이 같은 대상의 뒤 메시지가 먼저 나갔을 수 있어 받는 쪽에는 순서가 바뀌어 도착한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "되돌림",
			content = @Content(examples = @ExampleObject(value = OrderOutboxApiExamples.RETRY_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = OrderOutboxApiExamples.INVALID_VALUE_TYPE))),
		@ApiResponse(responseCode = "404", description = "없는 행",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = OrderOutboxApiExamples.NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "발행 실패 상태가 아님 (이미 보냈거나 아직 보내는 중)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = OrderOutboxApiExamples.RETRY_INVALID_STATUS)))
	})
	public ResponseEntity<RsData<OutboxFailureDto>> retry(
		@Parameter(description = "아웃박스 행 ID (Slack 알림의 행 ID)", example = "42")
		@PathVariable long id
	) {
		OutboxFailureDto event = orderKafkaFailureFacade.retryOutbox(id);
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "메시지를 다시 보내도록 되돌렸습니다.", event));
	}
}
