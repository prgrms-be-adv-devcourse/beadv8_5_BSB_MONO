package com.bukang.product.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.product.app.ProductFacade;
import com.bukang.product.app.SellerOnSaleRaceResult;
import com.bukang.product.dto.SellerOnSaleRaceDto;

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
 * 회원 서비스가 판매자 회원 탈퇴 전에 판매가 끝나지 않은 대회가 있는지 확인할 때 부른다 (CRU-34)
 * 대회를 다루지만 부르는 서비스가 달라 InternalV1RaceSellerController(문의 서비스용)와 컨트롤러를 나눈다
 */
@Tag(name = "SellerRaceInternal", description = "회원 서비스용 판매자의 끝나지 않은 대회 확인 API (서비스 간 내부 호출 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/product/sellers")
public class InternalV1SellerRaceController {
	private static final String CHECKED_MESSAGE = "판매가 끝나지 않은 대회가 있는지 조회했습니다.";
	private static final String NOT_SELLER_MESSAGE = "판매자로 등록된 회원이 아닙니다.";

	private final ProductFacade productFacade;

	@GetMapping("/{memberId}/races/on-sale")
	@Operation(summary = "판매자의 끝나지 않은 대회 여부 조회",
		description = "회원 서비스가 판매자 회원을 탈퇴시키기 전에 부른다. "
			+ "경로 값은 판매자의 회원 ID로, 대회 상세의 seller.sellerId(판매자 ID)와 다른 값이다. "
			+ "판매 예정·판매 중·판매 중지 대회가 하나라도 있으면 hasOnSaleRace가 true이고, "
			+ "판매 종료·대회 취소 대회만 있거나 대회가 없으면 false다. "
			+ "판매자로 등록되지 않은 회원이면 탈퇴를 막도록 true와 문구 \"판매자로 등록된 회원이 아닙니다.\"로 답한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (판매자로 등록되지 않은 회원도 200)",
			content = @Content(examples = {
				@ExampleObject(name = "끝나지 않은 대회 있음", value = SellerRaceInternalApiExamples.ON_SALE_RACE_EXISTS),
				@ExampleObject(name = "끝나지 않은 대회 없음", value = SellerRaceInternalApiExamples.NO_ON_SALE_RACE),
				@ExampleObject(name = "판매자 아님", value = SellerRaceInternalApiExamples.NOT_SELLER)
			})),
		@ApiResponse(responseCode = "400", description = "회원 ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerRaceInternalApiExamples.INVALID_VALUE_TYPE)))
	})
	public ResponseEntity<RsData<SellerOnSaleRaceDto>> findSellerOnSaleRace(
		@Parameter(description = "판매자의 회원 ID (대회의 판매자 ID가 아님)", example = "1")
		@PathVariable long memberId
	) {
		SellerOnSaleRaceResult result = productFacade.findSellerOnSaleRace(memberId);
		String message = switch (result.outcome()) {
			case CHECKED -> CHECKED_MESSAGE;
			case NOT_SELLER -> NOT_SELLER_MESSAGE;
		};
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, message, result.race()));
	}
}
