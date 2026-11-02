package com.bukang.member.in;

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
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.member.app.SellerFacade;
import com.bukang.member.dto.SellerDto;
import com.bukang.member.dto.SellerRejectRequestDto;

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

// 관리자 판별은 X-Member-Id 회원의 역할 칸으로 한다 (BE-020 결정 1). 관리자가 아니면 403
@Tag(name = "AdminSeller", description = "판매자 신청 목록, 승인, 반려 API (관리자 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/member/admin/sellers")
public class ApiV1AdminSellerController {
	private final SellerFacade sellerFacade;

	@GetMapping
	@Operation(summary = "판매자 신청 목록 조회",
		description = "관리자가 판매자 신청을 최근 신청부터 조회한다. status를 주면 그 상태만, 비우면 전체를 돌려준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = SellerApiExamples.FIND_ALL_SUCCESS))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "관리자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.NOT_ADMIN))),
		@ApiResponse(responseCode = "404", description = "헤더의 회원이 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.MEMBER_NOT_FOUND)))
	})
	public ResponseEntity<RsData<List<SellerDto>>> findAll(
		@LoginMemberId long memberId,
		@Parameter(description = "신청 상태 (비우면 전체)", example = "PENDING")
		@RequestParam(required = false) SellerStatus status
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "판매자 신청 목록을 조회했습니다.",
				sellerFacade.findAllForAdmin(memberId, status)));
	}

	@PostMapping("/{id}/approval")
	@Operation(summary = "판매자 신청 승인",
		description = "관리자가 대기 중인 판매자 신청을 승인한다. 커밋 뒤 판매자 변경 이벤트를 보낸다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "승인 성공",
			content = @Content(examples = @ExampleObject(value = SellerApiExamples.APPROVE_SUCCESS))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "관리자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.NOT_ADMIN))),
		@ApiResponse(responseCode = "404", description = "헤더의 회원 또는 판매자 신청이 없음",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "판매자 신청 없음", value = SellerApiExamples.SELLER_NOT_FOUND),
				@ExampleObject(name = "회원 없음", value = SellerApiExamples.MEMBER_NOT_FOUND)
			})),
		@ApiResponse(responseCode = "409", description = "이미 승인 또는 반려한 신청",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.ALREADY_REVIEWED)))
	})
	public ResponseEntity<RsData<SellerDto>> approve(
		@LoginMemberId long memberId,
		@Parameter(description = "판매자 신청 ID", example = "1")
		@PathVariable long id
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "판매자 신청을 승인했습니다.", sellerFacade.approve(memberId, id)));
	}

	@PostMapping("/{id}/rejection")
	@Operation(summary = "판매자 신청 반려",
		description = "관리자가 대기 중인 판매자 신청을 사유와 함께 반려한다. 사유는 신청자만 보고 이벤트에는 넣지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "반려 성공",
			content = @Content(examples = @ExampleObject(value = SellerApiExamples.REJECT_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 요청 본문 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = SellerApiExamples.REJECT_INVALID_INPUT),
				@ExampleObject(name = "요청 본문 형식 오류", value = SellerApiExamples.INVALID_BODY)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "관리자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.NOT_ADMIN))),
		@ApiResponse(responseCode = "404", description = "헤더의 회원 또는 판매자 신청이 없음",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "판매자 신청 없음", value = SellerApiExamples.SELLER_NOT_FOUND),
				@ExampleObject(name = "회원 없음", value = SellerApiExamples.MEMBER_NOT_FOUND)
			})),
		@ApiResponse(responseCode = "409", description = "이미 승인 또는 반려한 신청",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.ALREADY_REVIEWED)))
	})
	public ResponseEntity<RsData<SellerDto>> reject(
		@LoginMemberId long memberId,
		@Parameter(description = "판매자 신청 ID", example = "1")
		@PathVariable long id,
		@Valid @RequestBody SellerRejectRequestDto sellerRejectRequestDto
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "판매자 신청을 반려했습니다.",
				sellerFacade.reject(memberId, id, sellerRejectRequestDto.getReason())));
	}
}
