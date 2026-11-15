package com.bukang.product.in;

import java.time.YearMonth;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.product.app.ProductFacade;
import com.bukang.product.dto.CrewLowPaymentDto;

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
 * 회원 서비스가 매일 불러, 결제율 미달이 많은 크루에 크루 참가 제한(파티 생성 30일 금지)을 기록하는 것을 가정한다 (BUK-176)
 * 상품 서비스는 미달 크루를 알려 주기만 하고, 제한을 저장하거나 막지 않는다
 */
@Tag(name = "CrewPaymentInternal", description = "크루 결제율 미달 조회 API (서비스 간 내부 호출 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/product/crews")
public class InternalV1CrewPaymentController {
	private final ProductFacade productFacade;

	@GetMapping("/low-payment")
	@Operation(summary = "결제율 미달 크루 조회",
		description = "그 달에 크루 자리를 확보한 파티마다 결제율(결제한 자리 / 확보한 자리)을 계산해, "
			+ "25% 이하인 파티가 3개 이상인 크루를 크루 ID 순으로 돌려준다. "
			+ "결제한 자리는 판매된 자리만 세고, 결제한 뒤 환불한 자리는 결제하지 않은 자리로 센다. "
			+ "결제 대기 자리가 남은 파티와 대회가 취소된 파티는 세지 않는다. "
			+ "달이 끝나기 전에도 부를 수 있고, 그때까지 결제 기한이 끝난 파티로 센다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (없으면 빈 목록)",
			content = @Content(examples = {
				@ExampleObject(name = "미달 크루 있음", value = CrewPaymentInternalApiExamples.LOW_PAYMENT_SUCCESS),
				@ExampleObject(name = "미달 크루 없음", value = CrewPaymentInternalApiExamples.LOW_PAYMENT_EMPTY)
			})),
		@ApiResponse(responseCode = "400", description = "달 없음 또는 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "달 없음", value = CrewPaymentInternalApiExamples.MISSING_MONTH),
				@ExampleObject(name = "달 형식 오류", value = CrewPaymentInternalApiExamples.INVALID_VALUE_TYPE)
			}))
	})
	public ResponseEntity<RsData<List<CrewLowPaymentDto>>> findLowPaymentCrews(
		@Parameter(description = "자리를 확보한 달 (yyyy-MM)", example = "2026-11")
		@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "결제율 미달 크루를 조회했습니다.", productFacade.findLowPaymentCrews(month)));
	}
}
