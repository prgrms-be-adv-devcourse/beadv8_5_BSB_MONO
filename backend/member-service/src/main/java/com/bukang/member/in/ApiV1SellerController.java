package com.bukang.member.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.member.app.SellerFacade;
import com.bukang.member.dto.SellerApplyRequestDto;
import com.bukang.member.dto.SellerDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Seller", description = "판매자 등록 신청, 내 신청 조회, 신청 결과 확인 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/member/sellers")
public class ApiV1SellerController {
	private final SellerFacade sellerFacade;

	@PostMapping
	@Operation(summary = "판매자 등록 신청",
		description = "X-Member-Id 헤더의 회원이 판매자 등록을 신청한다. 사업자는 상호·사업자등록번호가 필수이고, "
			+ "비사업자가 보낸 상호·사업자등록번호는 버린다. 대기·승인 상태 신청이 있으면 막고, 반려만 있으면 새로 신청한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "신청 성공",
			content = @Content(examples = @ExampleObject(value = SellerApiExamples.APPLY_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 요청 본문 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = SellerApiExamples.APPLY_INVALID_INPUT),
				@ExampleObject(name = "요청 본문 형식 오류", value = SellerApiExamples.INVALID_BODY)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "헤더의 회원이 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.MEMBER_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "대기 또는 승인 상태 신청이 있음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.ALREADY_APPLIED)))
	})
	public ResponseEntity<RsData<SellerDto>> apply(
		@LoginMemberId long memberId,
		@Valid @RequestBody SellerApplyRequestDto sellerApplyRequestDto
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(RsData.of(HttpStatus.CREATED, "판매자 등록을 신청했습니다.",
				sellerFacade.apply(memberId, sellerApplyRequestDto)));
	}

	@GetMapping("/me")
	@Operation(summary = "내 판매자 신청 조회",
		description = "X-Member-Id 헤더의 회원의 가장 최근 판매자 신청을 조회한다. 반려됐으면 반려 사유도 담는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = SellerApiExamples.FIND_MINE_SUCCESS))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "판매자 신청 내역 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.MY_SELLER_NOT_FOUND)))
	})
	public ResponseEntity<RsData<SellerDto>> findMine(
		@LoginMemberId long memberId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "판매자 신청 내역을 조회했습니다.", sellerFacade.findMine(memberId)));
	}

	@PostMapping("/me/result-check")
	@Operation(summary = "내 판매자 신청 결과 확인",
		description = "X-Member-Id 헤더의 회원이 가장 최근 판매자 신청의 승인·반려 결과를 봤다고 기록한다. "
			+ "이미 기록했으면 처음 시각을 그대로 두고 200이다. 심사 중인 신청은 기록하지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "기록 성공 (이미 기록했어도 200)",
			content = @Content(examples = @ExampleObject(value = SellerApiExamples.CHECK_RESULT_SUCCESS))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "판매자 신청 내역 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.MY_SELLER_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "가장 최근 신청이 아직 심사 중",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.NOT_REVIEWED)))
	})
	public ResponseEntity<RsData<SellerDto>> checkMyResult(
		@LoginMemberId long memberId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "판매자 신청 결과를 확인했습니다.", sellerFacade.checkMyResult(memberId)));
	}
}
