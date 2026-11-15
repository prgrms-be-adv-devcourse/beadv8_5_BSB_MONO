package com.bukang.product.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.product.app.ProductFacade;
import com.bukang.product.dto.RaceSellerMemberDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 서비스끼리 부르는 내부 API. 게이트웨이에 라우팅하지 않는다(/internal/** 경로)
 * 문의 서비스의 RaceSellerPort(대회 판매자 조회)가 문의를 만들 때 부른다 (BUK-159)
 * 주소 앞부분은 정산용 InternalV1RaceScheduleController와 같지만, 부르는 서비스가 달라 컨트롤러를 나눈다
 */
@Tag(name = "RaceSellerInternal", description = "문의 서비스용 대회 판매자 조회 API (서비스 간 내부 호출 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/product/races")
public class InternalV1RaceSellerController {
	private final ProductFacade productFacade;

	@GetMapping("/{raceId}/seller")
	@Operation(summary = "대회 판매자의 회원 ID 조회",
		description = "문의 서비스가 문의를 그 대회 판매자에게 배정할 때 부른다. "
			+ "sellerMemberId는 문의를 받을 회원 ID로, 대회 상세의 seller.sellerId(판매자 ID)와 다른 값이다. "
			+ "취소·판매 종료된 대회도 돌려준다. 대회가 없으면 404이고 code는 RACE_NOT_FOUND다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = RaceSellerInternalApiExamples.SELLER_MEMBER_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "대회 ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceSellerInternalApiExamples.INVALID_VALUE_TYPE))),
		@ApiResponse(responseCode = "404", description = "없는 대회 (code: RACE_NOT_FOUND)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceSellerInternalApiExamples.RACE_NOT_FOUND)))
	})
	public ResponseEntity<RsData<RaceSellerMemberDto>> findSellerMember(
		@Parameter(description = "대회 ID", example = "1")
		@PathVariable long raceId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "대회 판매자를 조회했습니다.", productFacade.findRaceSellerMember(raceId)));
	}
}
