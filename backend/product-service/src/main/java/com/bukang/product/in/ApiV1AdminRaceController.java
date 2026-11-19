package com.bukang.product.in;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.product.app.ProductFacade;
import com.bukang.product.dto.RaceSaveDto;
import com.bukang.product.dto.SellerRaceDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

// 관리자 대회 관리 (CRU-44). 관리자인지는 회원 서비스에 묻는다 (게이트웨이는 토큰만 확인한다)
// 상태 규칙은 판매자 API(ApiV1RaceController의 open·stop·resume)와 같고, 대회 소유 확인만 없다
@Tag(name = "AdminRace", description = "관리자 대회 목록, 판매 시작·판매 중지·판매 다시 열기 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/product/admin/races")
public class ApiV1AdminRaceController {
	private final ProductFacade productFacade;

	@GetMapping
	@Operation(summary = "관리자 대회 목록",
		description = "모든 판매자의 대회를 최근 등록 순으로 모두 돌려준다(취소된 대회 포함). "
			+ "판매자 이름, 판매 상태, 종목별 정원·결제 대기·판매 수, 대회일, 접수 기간, 환불 기준일을 함께 준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = RaceApiExamples.ADMIN_RACES_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "회원 ID 형식 오류 또는 회원 ID가 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "회원 ID 형식 오류", value = RaceApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = RaceApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "관리자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.ADMIN_FORBIDDEN))),
		@ApiResponse(responseCode = "503", description = "회원 서비스에 연결하지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.MEMBER_SERVICE_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<List<SellerRaceDto>>> findAdminRaces(
		@LoginMemberId long memberId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "대회 목록을 조회했습니다.", productFacade.findAdminRaces(memberId)));
	}

	@PostMapping("/{raceId}/open")
	@Operation(summary = "관리자 판매 바로 시작",
		description = "판매 예정 대회의 판매를 지금 시작한다(판매 중). 판매자 API와 같은 규칙으로 판매 시작 일정을 지금으로 당기고 "
			+ "판매 시작일을 오늘로 바꾼다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "판매 시작 성공",
			content = @Content(examples = @ExampleObject(value = RaceApiExamples.OPEN_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "대회 ID·회원 ID 형식 오류 또는 회원 ID가 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "대회 ID·회원 ID 형식 오류", value = RaceApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = RaceApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "관리자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.ADMIN_FORBIDDEN))),
		@ApiResponse(responseCode = "404", description = "없는 대회",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.RACE_NOT_FOUND))),
		@ApiResponse(responseCode = "409",
			description = "판매 예정이 아님, 접수 마감이 지남, 판매 시작 시각이 지남 또는 그 사이 상태가 바뀜",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "이미 판매 중", value = RaceApiExamples.OPEN_ALREADY_ON_SALE),
				@ExampleObject(name = "판매 중지", value = RaceApiExamples.OPEN_STOPPED),
				@ExampleObject(name = "판매 종료", value = RaceApiExamples.SALE_CLOSED),
				@ExampleObject(name = "취소된 대회", value = RaceApiExamples.RACE_CANCELED),
				@ExampleObject(name = "접수 마감이 지남", value = RaceApiExamples.OPEN_AFTER_APPLICATION_CLOSE),
				@ExampleObject(name = "판매 시작 시각이 지남", value = RaceApiExamples.OPEN_AFTER_SALE_OPEN_TIME),
				@ExampleObject(name = "그 사이 상태가 바뀜", value = RaceApiExamples.SALE_STATUS_CHANGED)
			})),
		@ApiResponse(responseCode = "503", description = "회원 서비스에 연결하지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.MEMBER_SERVICE_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<RaceSaveDto>> openByAdmin(
		@LoginMemberId long memberId,
		@Parameter(description = "대회 ID", example = "1")
		@PathVariable long raceId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "대회 판매를 시작했습니다.", productFacade.openRaceByAdmin(memberId, raceId)));
	}

	@PostMapping("/{raceId}/stop")
	@Operation(summary = "관리자 판매 중지",
		description = "판매 중인 대회의 판매를 멈춘다(판매 중지). 판매자 API와 같이 접수 마감 전까지만 멈출 수 있다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "판매 중지 성공",
			content = @Content(examples = @ExampleObject(value = RaceApiExamples.STOP_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "대회 ID·회원 ID 형식 오류 또는 회원 ID가 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "대회 ID·회원 ID 형식 오류", value = RaceApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = RaceApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "관리자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.ADMIN_FORBIDDEN))),
		@ApiResponse(responseCode = "404", description = "없는 대회",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.RACE_NOT_FOUND))),
		@ApiResponse(responseCode = "409",
			description = "판매 중이 아님(판매 예정·이미 판매 중지·판매 종료·취소), 접수 마감이 지남 또는 그 사이 상태가 바뀜",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "판매 시작 전", value = RaceApiExamples.STOP_BEFORE_SALE),
				@ExampleObject(name = "이미 판매 중지", value = RaceApiExamples.ALREADY_STOPPED),
				@ExampleObject(name = "접수 마감이 지남", value = RaceApiExamples.STOP_AFTER_APPLICATION_CLOSE),
				@ExampleObject(name = "그 사이 상태가 바뀜", value = RaceApiExamples.SALE_STATUS_CHANGED)
			})),
		@ApiResponse(responseCode = "503", description = "회원 서비스에 연결하지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.MEMBER_SERVICE_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<RaceSaveDto>> stopByAdmin(
		@LoginMemberId long memberId,
		@Parameter(description = "대회 ID", example = "1")
		@PathVariable long raceId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "대회 판매를 멈췄습니다.", productFacade.stopRaceByAdmin(memberId, raceId)));
	}

	@PostMapping("/{raceId}/resume")
	@Operation(summary = "관리자 판매 다시 열기",
		description = "판매 중지 대회의 판매를 다시 연다(판매 중). 판매자 API와 같이 접수 마감 전까지만 열 수 있다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "다시 열기 성공",
			content = @Content(examples = @ExampleObject(value = RaceApiExamples.RESUME_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "대회 ID·회원 ID 형식 오류 또는 회원 ID가 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "대회 ID·회원 ID 형식 오류", value = RaceApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = RaceApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "관리자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.ADMIN_FORBIDDEN))),
		@ApiResponse(responseCode = "404", description = "없는 대회",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.RACE_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "판매 중지가 아님, 접수 마감이 지남 또는 그 사이 상태가 바뀜",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "판매 중지가 아님", value = RaceApiExamples.RESUME_NOT_STOPPED),
				@ExampleObject(name = "접수 마감이 지남", value = RaceApiExamples.RESUME_AFTER_APPLICATION_CLOSE),
				@ExampleObject(name = "그 사이 상태가 바뀜", value = RaceApiExamples.SALE_STATUS_CHANGED)
			})),
		@ApiResponse(responseCode = "503", description = "회원 서비스에 연결하지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.MEMBER_SERVICE_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<RaceSaveDto>> resumeByAdmin(
		@LoginMemberId long memberId,
		@Parameter(description = "대회 ID", example = "1")
		@PathVariable long raceId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "대회 판매를 다시 열었습니다.", productFacade.resumeRaceByAdmin(memberId, raceId)));
	}
}
