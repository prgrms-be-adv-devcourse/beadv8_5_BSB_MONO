package com.bukang.product.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.product.app.ProductFacade;
import com.bukang.product.dto.CrewHoldRequestDto;
import com.bukang.product.dto.CrewHoldResultDto;

import io.swagger.v3.oas.annotations.Operation;
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
 * 주문 서비스의 CrewSeatHoldPort.holdAll(파티 신청)이 부른다. 파티장인지는 주문 서비스가 확인하고 부르므로 회원 ID를 받지 않는다.
 * 잡은 크루 자리의 판매는 개인 자리와 같은 판매 API(/internal/v1/product/holds/{participationId}/sell)를 쓰고,
 * 해제·복구는 주문 서비스가 Kafka(product.hold.cancel, product.hold.restore)로 알린다.
 * 자리가 모자란 것도 오류가 아니라 200과 결과 칸으로 돌려준다 (주문 서비스 CrewSeatHoldResult와 같은 모양).
 */
@Tag(name = "RaceCrewHold", description = "주문 서비스용 파티 크루 자리 확보 API (서비스 간 내부 호출 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/product/crew-holds")
public class InternalV1CrewHoldController {
	private final ProductFacade productFacade;

	@PostMapping
	@Operation(summary = "크루 자리 확보",
		description = "파티장이 [대회 참가]를 누르면 파티원 수만큼 크루 자리를 결제 대기로 한꺼번에 확보한다. "
			+ "종목 하나라도 남은 크루 자리가 모자라면 한 석도 잡지 않고 held=false와 모자란 종목(필요한 수·남은 수)으로 응답한다. "
			+ "자리는 요청한 끝 시각(파티원 결제 기한)까지만 유지한다. "
			+ "같은 파티 ID로 다시 요청하면 새 자리를 잡지 않고 처음 결과를 돌려주고, 자리 목록이 처음과 다르면 409로 응답한다. "
			+ "판매 예정·판매 중인 대회의 크루 예매 시작부터 크루 예매 마감 전까지(접수 마감 전)만 확보할 수 있다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "확보 성공 또는 크루 자리 모자람",
			content = @Content(examples = {
				@ExampleObject(name = "확보 성공", value = RaceHoldApiExamples.CREW_HOLD_SUCCESS),
				@ExampleObject(name = "크루 자리 모자람", value = RaceHoldApiExamples.CREW_HOLD_SHORTAGE)
			})),
		@ApiResponse(responseCode = "400",
			description = "입력값 검증 실패, 끝 시각이 이미 지남, 같은 참가 신청이 두 번 들어 있음 또는 요청 본문 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = RaceHoldApiExamples.CREW_HOLD_INVALID_INPUT),
				@ExampleObject(name = "끝 시각이 이미 지남", value = RaceHoldApiExamples.CREW_HOLD_EXPIRES_PASSED),
				@ExampleObject(name = "같은 참가 신청이 두 번 들어 있음",
					value = RaceHoldApiExamples.CREW_HOLD_DUPLICATED_PARTICIPATION),
				@ExampleObject(name = "요청 본문 형식 오류", value = RaceApiExamples.INVALID_BODY)
			})),
		@ApiResponse(responseCode = "404", description = "없는 대회 또는 없는 종목",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "없는 대회", value = RaceApiExamples.RACE_NOT_FOUND),
				@ExampleObject(name = "없는 종목", value = RaceInternalApiExamples.COURSE_NOT_FOUND)
			})),
		@ApiResponse(responseCode = "409",
			description = "판매를 멈춘 대회, 크루 예매 기간이 아님(대회 취소 포함), 종목이 요청한 대회의 것이 아님, "
				+ "다른 자리 목록으로 확보한 파티, 이미 자리를 확보한 참가 신청이 들어 있음 또는 이미 끝난 참가 신청",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "판매를 멈춘 대회", value = RaceHoldApiExamples.SALE_STOPPED),
				@ExampleObject(name = "크루 예매 기간이 아님", value = RaceHoldApiExamples.CREW_NOT_ACCEPTING),
				@ExampleObject(name = "다른 대회의 종목", value = RaceHoldApiExamples.CREW_HOLD_COURSE_NOT_IN_RACE),
				@ExampleObject(name = "다른 자리 목록으로 확보한 파티", value = RaceHoldApiExamples.CREW_HOLD_MISMATCH),
				@ExampleObject(name = "이미 자리를 확보한 참가 신청",
					value = RaceHoldApiExamples.CREW_HOLD_PARTICIPATION_TAKEN),
				@ExampleObject(name = "이미 끝난 참가 신청", value = RaceHoldApiExamples.HOLD_FINISHED)
			}))
	})
	public ResponseEntity<RsData<CrewHoldResultDto>> hold(@Valid @RequestBody CrewHoldRequestDto request) {
		CrewHoldResultDto result = productFacade.holdCrewSeats(request);
		String message = result.isHeld() ? "크루 자리를 확보했습니다." : "남은 크루 자리가 모자랍니다.";
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, message, result));
	}
}
