package com.bukang.product.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.product.app.ProductFacade;
import com.bukang.product.dto.HoldRequestDto;
import com.bukang.product.dto.HoldRestoreResultDto;
import com.bukang.product.dto.HoldResultDto;
import com.bukang.product.dto.HoldSellResult;
import com.bukang.product.dto.HoldSellResultDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 서비스끼리 부르는 내부 API. 게이트웨이에 라우팅하지 않는다(/internal/** 경로)
 * 주문 서비스의 SeatHoldPort(자리 확보·판매·복구)가 부른다. 해제는 API가 없고 주문 서비스가 Kafka(product.hold.cancel)로 알린다.
 * 매진·기한 지남 같은 결과도 오류가 아니라 200과 결과 칸으로 돌려준다 (주문 서비스 SeatHoldResult·SeatSellResult와 같은 모양).
 */
@Tag(name = "RaceHold", description = "주문 서비스용 자리 확보, 판매, 복구 API (서비스 간 내부 호출 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/product/holds")
public class InternalV1HoldController {
	private final ProductFacade productFacade;

	@PostMapping
	@Operation(summary = "개인 자리 확보",
		description = "참가자가 [결제하기]를 누르면 개인 자리 1석을 결제 대기로 확보한다. 남은 자리가 없으면 held=false(매진)로 응답한다. "
			+ "자리는 요청한 끝 시각(대기열 활성 시간이 끝나는 시각)까지만 유지하고, 끝나면 주문 서비스가 Kafka로 해제를 알린다. "
			+ "같은 참가 신청 ID로 다시 요청하면 새 자리를 잡지 않고 처음 결과를 돌려준다. "
			+ "판매 예정·판매 중인 대회의 개인 예매 오픈부터 접수 마감 전까지만 확보할 수 있다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "확보 성공 또는 매진",
			content = @Content(examples = {
				@ExampleObject(name = "확보 성공", value = RaceHoldApiExamples.HOLD_SUCCESS),
				@ExampleObject(name = "매진", value = RaceHoldApiExamples.HOLD_SOLD_OUT)
			})),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패, 끝 시각이 이미 지남 또는 요청 본문 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = RaceHoldApiExamples.HOLD_INVALID_INPUT),
				@ExampleObject(name = "끝 시각이 이미 지남", value = RaceHoldApiExamples.HOLD_EXPIRES_PASSED),
				@ExampleObject(name = "요청 본문 형식 오류", value = RaceApiExamples.INVALID_BODY)
			})),
		@ApiResponse(responseCode = "404", description = "없는 종목",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceInternalApiExamples.COURSE_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "접수 기간이 아님(판매 중지·대회 취소 포함) 또는 이미 끝난 참가 신청",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "접수 기간이 아님", value = RaceHoldApiExamples.NOT_ACCEPTING),
				@ExampleObject(name = "이미 끝난 참가 신청", value = RaceHoldApiExamples.HOLD_FINISHED)
			}))
	})
	public ResponseEntity<RsData<HoldResultDto>> hold(@Valid @RequestBody HoldRequestDto request) {
		HoldResultDto result = productFacade.holdSeat(request);
		String message = result.isHeld() ? "자리를 확보했습니다." : "남은 자리가 없습니다.";
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, message, result));
	}

	@PostMapping("/{participationId}/sell")
	@Operation(summary = "자리 판매",
		description = "결제가 끝난 결제 대기 자리를 판매로 바꾼다. 끝 시각이 지났거나 이미 해제된 자리, 취소된 대회의 자리는 "
			+ "판매하지 않고 result=EXPIRED로 응답한다(주문 서비스는 결제를 취소하고 예치금을 돌려준다). "
			+ "이미 판매한 자리면 끝 시각과 상관없이 SOLD로 응답한다(같은 주문으로 다시 결제할 때).")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "판매 또는 기한 지남",
			content = @Content(examples = {
				@ExampleObject(name = "판매", value = RaceHoldApiExamples.SELL_SOLD),
				@ExampleObject(name = "기한 지남", value = RaceHoldApiExamples.SELL_EXPIRED)
			})),
		@ApiResponse(responseCode = "400", description = "참가 신청 ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.INVALID_VALUE_TYPE)))
	})
	public ResponseEntity<RsData<HoldSellResultDto>> sell(
		@Parameter(description = "자리를 확보할 때 보낸 참가 신청 ID", example = "1001")
		@PathVariable long participationId
	) {
		HoldSellResult result = productFacade.sellSeat(participationId);
		String message = result == HoldSellResult.SOLD ? "자리를 판매했습니다." : "결제 기한이 지난 자리입니다.";
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, message, new HoldSellResultDto(result)));
	}

	@PostMapping("/{participationId}/restore")
	@Operation(summary = "판매한 자리 복구",
		description = "결제한 참가권을 취소하면 판매한 자리를 판매 가능 정원으로 되돌린다. "
			+ "여러 번 불러도 한 번만 되돌리고, 되돌릴 판매 자리가 없으면 restored=false로 응답한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "복구했거나 되돌릴 자리가 없음",
			content = @Content(examples = {
				@ExampleObject(name = "복구", value = RaceHoldApiExamples.RESTORE_SUCCESS),
				@ExampleObject(name = "되돌릴 자리 없음", value = RaceHoldApiExamples.RESTORE_NOTHING)
			})),
		@ApiResponse(responseCode = "400", description = "참가 신청 ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.INVALID_VALUE_TYPE)))
	})
	public ResponseEntity<RsData<HoldRestoreResultDto>> restore(
		@Parameter(description = "자리를 확보할 때 보낸 참가 신청 ID", example = "1001")
		@PathVariable long participationId
	) {
		boolean restored = productFacade.restoreSeat(participationId);
		String message = restored ? "자리를 정원으로 되돌렸습니다." : "되돌릴 판매 자리가 없습니다.";
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, message, new HoldRestoreResultDto(restored)));
	}
}
