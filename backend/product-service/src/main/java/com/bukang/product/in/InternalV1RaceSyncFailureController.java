package com.bukang.product.in;

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
import com.bukang.product.app.ProductFacade;
import com.bukang.product.domain.RaceSyncType;
import com.bukang.product.dto.RaceSyncCompleteRequestDto;
import com.bukang.product.dto.RaceSyncEventDto;

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
 * 처리 실패한 검색 문서 동기화(문서 동기화·AI 추천 벡터 동기화)를 보고 재처리하는 내부 API (BUK-162).
 * 게이트웨이에 라우팅하지 않는다(/internal/** 경로, Nginx도 404로 막는다). 사람이 Slack 알림을 받고 서버 안에서 curl로 부른다.
 * 예) docker compose exec product-service \
 *       curl -X POST localhost:8085/internal/v1/product/race-sync-failures/embeddings/42/retry
 * 재처리는 행을 미처리로 되돌리기만 한다. 스케줄러가 곧 다시 처리하고, 처리되면 Slack으로 완료를 알린다.
 * 문서 동기화는 사람이 문서를 직접 고친 뒤 메모와 함께 닫을 수도 있다. 임베딩 동기화는 재처리만 있다.
 * 클래스에 @Validated를 붙이지 않는다 (InternalV1ProductDltController와 같은 이유: 조회 건수 검사가 500이 된다).
 */
@Tag(name = "RaceSyncFailure",
	description = "검색 문서·AI 추천 벡터 동기화 처리 실패 목록, 재처리, 문서 동기화 처리 완료 API (운영자 내부 호출 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/product/race-sync-failures")
public class InternalV1RaceSyncFailureController {
	private final ProductFacade productFacade;

	@GetMapping("/documents")
	@Operation(summary = "검색 문서 동기화 처리 실패 목록", description = "처리 실패한 검색 문서 동기화 행을 최근 것부터 size건 돌려준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = RaceSyncFailureApiExamples.LIST_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "조회 건수 범위 밖",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceSyncFailureApiExamples.LIST_SIZE_OUT_OF_RANGE)))
	})
	public ResponseEntity<RsData<List<RaceSyncEventDto>>> listDocumentFailures(
		@Parameter(description = "조회 건수 (1~100)", example = "50")
		@RequestParam(defaultValue = "50")
		@Min(value = 1, message = "조회 건수는 1 이상이어야 합니다.")
		@Max(value = 100, message = "한 번에 100건까지 조회할 수 있습니다.")
		int size
	) {
		return list(RaceSyncType.DOCUMENT, size);
	}

	@GetMapping("/embeddings")
	@Operation(summary = "AI 추천 벡터 동기화 처리 실패 목록",
		description = "처리 실패한 AI 추천 벡터 동기화 행을 최근 것부터 size건 돌려준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = RaceSyncFailureApiExamples.LIST_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "조회 건수 범위 밖",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceSyncFailureApiExamples.LIST_SIZE_OUT_OF_RANGE)))
	})
	public ResponseEntity<RsData<List<RaceSyncEventDto>>> listEmbeddingFailures(
		@Parameter(description = "조회 건수 (1~100)", example = "50")
		@RequestParam(defaultValue = "50")
		@Min(value = 1, message = "조회 건수는 1 이상이어야 합니다.")
		@Max(value = 100, message = "한 번에 100건까지 조회할 수 있습니다.")
		int size
	) {
		return list(RaceSyncType.EMBEDDING, size);
	}

	@PostMapping("/documents/{id}/retry")
	@Operation(summary = "검색 문서 동기화 재처리",
		description = "처리 실패한 행을 미처리로 되돌린다(실패 횟수 0). 스케줄러가 1초 안에 다시 처리하고, 처리되면 Slack으로 완료를 알린다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "재처리 요청됨",
			content = @Content(examples = @ExampleObject(value = RaceSyncFailureApiExamples.RETRY_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceSyncFailureApiExamples.INVALID_VALUE_TYPE))),
		@ApiResponse(responseCode = "404", description = "없는 행",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceSyncFailureApiExamples.NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "처리 실패 상태가 아님 (미처리이거나 처리 완료)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceSyncFailureApiExamples.RETRY_INVALID_STATUS)))
	})
	public ResponseEntity<RsData<RaceSyncEventDto>> retryDocument(
		@Parameter(description = "동기화 행 ID (Slack 알림의 행 ID)", example = "42")
		@PathVariable long id
	) {
		return retry(RaceSyncType.DOCUMENT, id);
	}

	@PostMapping("/embeddings/{id}/retry")
	@Operation(summary = "AI 추천 벡터 동기화 재처리",
		description = "처리 실패한 행을 미처리로 되돌린다(실패 횟수 0). 스케줄러가 1초 안에 다시 처리하고, 처리되면 Slack으로 완료를 알린다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "재처리 요청됨",
			content = @Content(examples = @ExampleObject(value = RaceSyncFailureApiExamples.RETRY_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceSyncFailureApiExamples.INVALID_VALUE_TYPE))),
		@ApiResponse(responseCode = "404", description = "없는 행",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceSyncFailureApiExamples.NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "처리 실패 상태가 아님 (미처리이거나 처리 완료)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceSyncFailureApiExamples.RETRY_INVALID_STATUS)))
	})
	public ResponseEntity<RsData<RaceSyncEventDto>> retryEmbedding(
		@Parameter(description = "동기화 행 ID (Slack 알림의 행 ID)", example = "42")
		@PathVariable long id
	) {
		return retry(RaceSyncType.EMBEDDING, id);
	}

	// 임베딩 동기화에는 두지 않는다. 벡터는 사람이 만들 수 없어 원인을 고친 뒤 재처리하는 길만 있다
	@PostMapping("/documents/{id}/complete")
	@Operation(summary = "검색 문서 동기화 처리 완료",
		description = "사람이 검색 문서를 직접 고친 뒤(예: Kibana) 처리 실패한 행을 다시 처리하지 않고 메모와 함께 닫는다. "
			+ "처리 실패 상태에서만 닫을 수 있고, 닫으면 Slack으로 완료를 알린다. "
			+ "닫은 뒤에도 맞추기 스케줄러(10분)가 ES 문서 수가 종목 수와 다르면 DB 기준으로 다시 맞춘다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 완료",
			content = @Content(examples = @ExampleObject(value = RaceSyncFailureApiExamples.COMPLETE_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "메모 없음, 메모가 500자 초과, 요청 본문 형식 오류 또는 ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "메모 없음", value = RaceSyncFailureApiExamples.COMPLETE_MEMO_BLANK),
				@ExampleObject(name = "메모가 500자 초과", value = RaceSyncFailureApiExamples.COMPLETE_MEMO_TOO_LONG),
				@ExampleObject(name = "요청 본문 형식 오류", value = RaceSyncFailureApiExamples.INVALID_BODY),
				@ExampleObject(name = "ID 형식 오류", value = RaceSyncFailureApiExamples.INVALID_VALUE_TYPE)
			})),
		@ApiResponse(responseCode = "404", description = "없는 행",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceSyncFailureApiExamples.NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "처리 실패 상태가 아님 (미처리이거나 처리 완료)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceSyncFailureApiExamples.COMPLETE_INVALID_STATUS)))
	})
	public ResponseEntity<RsData<RaceSyncEventDto>> completeDocument(
		@Parameter(description = "동기화 행 ID (Slack 알림의 행 ID)", example = "42")
		@PathVariable long id,
		@Valid @RequestBody RaceSyncCompleteRequestDto request
	) {
		RaceSyncEventDto event = productFacade.completeRaceDocumentSync(id, request.memo());
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "처리 완료로 바꿨습니다.", event));
	}

	private ResponseEntity<RsData<List<RaceSyncEventDto>>> list(RaceSyncType type, int size) {
		List<RaceSyncEventDto> events = productFacade.findRaceSyncFailures(type, size);
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "처리 실패한 동기화 행을 조회했습니다.", events));
	}

	private ResponseEntity<RsData<RaceSyncEventDto>> retry(RaceSyncType type, long id) {
		RaceSyncEventDto event = productFacade.retryRaceSync(type, id);
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "재처리를 요청했습니다.", event));
	}
}
