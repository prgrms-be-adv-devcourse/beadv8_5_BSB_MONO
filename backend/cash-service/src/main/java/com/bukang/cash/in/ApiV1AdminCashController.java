package com.bukang.cash.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.cash.app.AdminCashFacade;
import com.bukang.cash.dto.PageDto;
import com.bukang.cash.dto.charge.ChargeReviewDto;
import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "AdminCash", description = "관리자 고액 충전 확인 API (관리자 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cash/admin")
public class ApiV1AdminCashController {
	private final AdminCashFacade adminCashFacade;

	@GetMapping("/charge-reviews")
	@Operation(summary = "고액 충전 확인 목록 조회 (관리자)",
		description = "가입 후 24시간 안에 완료된 충전 합계가 50만 원을 넘어 검토 표시가 붙은 충전을, 표시된 충전의 완료 시각 최신순으로 "
			+ "돌려준다. 표시는 회원마다 한 건이라 한 줄이 회원 한 명이다. 페이지 번호·크기가 범위 밖이면 가까운 값(0, 1~100)으로 맞춘다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (표시된 충전이 없으면 빈 목록)",
			content = @Content(examples = {
				@ExampleObject(name = "표시 있음", value = AdminCashApiExamples.CHARGE_REVIEWS_SUCCESS),
				@ExampleObject(name = "표시 없음", value = AdminCashApiExamples.CHARGE_REVIEWS_EMPTY)
			})),
		@ApiResponse(responseCode = "400", description = "페이지 값 형식 오류 또는 회원 ID가 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "값 형식 오류", value = CashApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = CashApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CashApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "관리자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = AdminCashApiExamples.NOT_ADMIN))),
		@ApiResponse(responseCode = "503", description = "회원 서비스에 관리자 여부를 묻지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = AdminCashApiExamples.MEMBER_SERVICE_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<PageDto<ChargeReviewDto>>> getChargeReviews(
		@LoginMemberId long memberId,
		@Parameter(description = "페이지 번호 (0부터, 음수면 0으로 본다)", example = "0")
		@RequestParam(defaultValue = "0") int page,
		@Parameter(description = "페이지 크기 (1~100, 범위 밖이면 가까운 값으로 맞춘다)", example = "20")
		@RequestParam(defaultValue = "20") int size
	) {
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "고액 충전 확인 목록을 조회했습니다.",
			adminCashFacade.findChargeReviews(memberId, page, size)));
	}
}
