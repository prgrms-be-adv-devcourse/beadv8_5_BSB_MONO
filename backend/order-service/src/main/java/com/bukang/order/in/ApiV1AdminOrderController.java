package com.bukang.order.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.order.app.AdminOrderFacade;
import com.bukang.order.dto.AdminOrderPageDto;

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

@Tag(name = "AdminOrder", description = "관리자 회원·주문 조회 API (관리자 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/order/admin/orders")
public class ApiV1AdminOrderController {
	private static final int MAX_PAGE_SIZE = 100;

	private final AdminOrderFacade adminOrderFacade;

	@GetMapping
	@Operation(summary = "회원·주문 조회 (관리자)",
		description = "관리자가 회원 닉네임 또는 주문번호 하나로 찾은 주문을 결제 대기·기한 만료·취소까지 모두 최신순으로 돌려준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (회원은 있는데 주문이 없거나, 없는 주문번호면 빈 목록)",
			content = @Content(examples = @ExampleObject(value = AdminOrderApiExamples.LIST_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "검색 조건·페이지 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "닉네임·주문번호 둘 다 없거나 둘 다 있음", value = AdminOrderApiExamples.INVALID_SEARCH),
				@ExampleObject(name = "페이지 크기 초과", value = AdminOrderApiExamples.INVALID_PAGE)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = AdminOrderApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "관리자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = AdminOrderApiExamples.NOT_ADMIN))),
		@ApiResponse(responseCode = "404", description = "해당 닉네임의 회원이 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = AdminOrderApiExamples.MEMBER_NOT_FOUND))),
		@ApiResponse(responseCode = "503", description = "회원 서비스에 연결하지 못함, 또는 닉네임 검색을 아직 쓸 수 없음",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "회원 서비스 연결 실패", value = AdminOrderApiExamples.MEMBER_UNAVAILABLE),
				@ExampleObject(name = "닉네임 검색 막힘(운영)", value = AdminOrderApiExamples.NICKNAME_DISABLED)
			}))
	})
	public ResponseEntity<RsData<AdminOrderPageDto>> findOrders(
		@LoginMemberId long memberId,
		@Parameter(description = "회원 닉네임 (주문번호와 둘 중 하나)", example = "사용자1")
		@RequestParam(required = false) String nickname,
		@Parameter(description = "주문번호 (닉네임과 둘 중 하나, 대소문자 상관없음)", example = "OD-20261004-0012")
		@RequestParam(required = false) String orderNo,
		@Parameter(description = "페이지 번호 (0부터)", example = "0")
		@RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") int page,
		@Parameter(description = "페이지 크기 (1~100)", example = "20")
		@RequestParam(defaultValue = "20") @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
		@Max(value = MAX_PAGE_SIZE, message = "한 번에 100개까지 조회할 수 있습니다.") int size
	) {
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "회원 주문을 조회했습니다.",
			adminOrderFacade.findOrders(memberId, nickname, orderNo, page, size)));
	}
}
