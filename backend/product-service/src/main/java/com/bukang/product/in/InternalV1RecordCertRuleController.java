package com.bukang.product.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.product.app.ProductFacade;
import com.bukang.product.dto.RecordCertRuleDto;

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
 * member 서비스가 기록증을 제출 · 판정 · 심사할 때 부른다 (BUK-270)
 * 주소 앞부분은 주문용 InternalV1RaceCourseController와 같지만, 부르는 서비스가 달라 컨트롤러를 나눈다
 */
@Tag(name = "RecordCertRuleInternal", description = "member 서비스용 종목 기록증 규칙 조회 API (서비스 간 내부 호출 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/product/courses")
public class InternalV1RecordCertRuleController {
	private final ProductFacade productFacade;

	@GetMapping("/{raceCourseId}/record-cert-rule")
	@Operation(summary = "종목 기록증 규칙 조회",
		description = "종목의 기록증 규칙과 대회 판매자의 회원 ID를 돌려준다. 규칙이 없는 종목은 rule이 null이다. "
			+ "recognizedSince는 기록 인정 시작일(당일 포함), maxNetSeconds는 기준 넷타임(초, 같으면 통과), "
			+ "allowedCourseTypes는 인정 종목 구분(KM_5, KM_10, HALF, FULL, ULTRA)이다. "
			+ "sellerMemberId는 판매자 ID(product_seller.id)가 아니라 회원 ID다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = {
				@ExampleObject(name = "규칙 있음", value = RecordCertRuleInternalApiExamples.RULE_SUCCESS),
				@ExampleObject(name = "규칙 없음", value = RecordCertRuleInternalApiExamples.NO_RULE_SUCCESS)
			})),
		@ApiResponse(responseCode = "400", description = "종목 ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RecordCertRuleInternalApiExamples.INVALID_VALUE_TYPE))),
		@ApiResponse(responseCode = "404", description = "없는 종목",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RecordCertRuleInternalApiExamples.COURSE_NOT_FOUND)))
	})
	public ResponseEntity<RsData<RecordCertRuleDto>> findRecordCertRule(
		@Parameter(description = "종목 ID", example = "1")
		@PathVariable long raceCourseId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "종목 기록증 규칙을 조회했습니다.", productFacade.findRecordCertRule(raceCourseId)));
	}
}
