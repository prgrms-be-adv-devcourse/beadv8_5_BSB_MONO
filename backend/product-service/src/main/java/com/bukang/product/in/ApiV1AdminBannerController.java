package com.bukang.product.in;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.product.app.ProductFacade;
import com.bukang.product.dto.AdminBannerDto;
import com.bukang.product.dto.HomeBannerSaveRequestDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 관리자 홈 배너 (CRU-147). 관리자인지는 관리자 대회 API와 같이 회원 서비스에 묻는다
@Tag(name = "AdminBanner", description = "관리자 홈 배너 조회, 저장 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/product/admin/banners")
public class ApiV1AdminBannerController {
	private final ProductFacade productFacade;

	@GetMapping
	@Operation(summary = "관리자 홈 배너 목록",
		description = "배너로 고른 대회를 순서대로 돌려준다. 접수 마감·판매 중지·판매 종료·취소된 대회도 "
			+ "홈에 보이지 않는 이유(hiddenReason)와 함께 남는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = BannerApiExamples.ADMIN_BANNERS_SUCCESS))),
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
	public ResponseEntity<RsData<List<AdminBannerDto>>> findAdminBanners(
		@LoginMemberId long memberId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "홈 배너 목록을 조회했습니다.", productFacade.findAdminBanners(memberId)));
	}

	@PutMapping
	@Operation(summary = "관리자 홈 배너 저장",
		description = "배너 목록 전체를 보낸 순서대로 한 번에 바꾼다(최대 5개, 빈 목록이면 모두 비운다). "
			+ "판매 중지·접수 마감 대회는 고를 수 있지만 홈에는 보이지 않는다. 저장한 목록을 관리자 목록 모양으로 돌려준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "저장 성공",
			content = @Content(examples = @ExampleObject(value = BannerApiExamples.SAVE_SUCCESS))),
		@ApiResponse(responseCode = "400",
			description = "6개 이상, 같은 대회 중복, 목록 없음, 본문 형식 오류, 회원 ID 형식 오류 또는 회원 ID가 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "6개 이상", value = BannerApiExamples.TOO_MANY),
				@ExampleObject(name = "같은 대회 중복", value = BannerApiExamples.DUPLICATE_RACE),
				@ExampleObject(name = "목록 없음", value = BannerApiExamples.MISSING_RACE_IDS),
				@ExampleObject(name = "본문 형식 오류", value = BannerApiExamples.INVALID_BODY),
				@ExampleObject(name = "회원 ID 형식 오류", value = RaceApiExamples.INVALID_VALUE_TYPE),
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
		@ApiResponse(responseCode = "409", description = "취소된 대회 또는 판매가 끝난 대회",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "취소된 대회", value = BannerApiExamples.CANCELED_RACE),
				@ExampleObject(name = "판매가 끝난 대회", value = BannerApiExamples.CLOSED_RACE)
			})),
		@ApiResponse(responseCode = "503", description = "회원 서비스에 연결하지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.MEMBER_SERVICE_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<List<AdminBannerDto>>> saveAdminBanners(
		@LoginMemberId long memberId,
		@Valid @RequestBody HomeBannerSaveRequestDto request
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "홈 배너를 저장했습니다.",
				productFacade.saveAdminBanners(memberId, request.raceIds())));
	}
}
