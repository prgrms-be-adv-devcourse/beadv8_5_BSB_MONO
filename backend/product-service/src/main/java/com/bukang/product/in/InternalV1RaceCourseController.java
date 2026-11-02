package com.bukang.product.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.product.app.ProductFacade;
import com.bukang.product.dto.RaceCourseInfoDto;

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
 * 주문 서비스의 RaceCoursePort(종목 정보 조회)가 부른다.
 */
@Tag(name = "RaceInternal", description = "주문 서비스용 종목 정보 API (서비스 간 내부 호출 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/product/courses")
public class InternalV1RaceCourseController {
	private final ProductFacade productFacade;

	@GetMapping("/{raceCourseId}")
	@Operation(summary = "주문용 종목 정보 조회",
		description = "참가 신청·결제·취소에 필요한 종목과 대회 정보를 돌려준다. 필드 이름은 주문 서비스 RaceCourseInfo와 같다. "
			+ "title은 대회명, course는 종목명, courseName은 주문 상품명으로 남길 '대회명 종목명'이다. "
			+ "price가 구매자가 내는 참가권 한 장 값이고 kitPrice는 그 안에 포함된 키트 금액이다(더하지 않는다). "
			+ "scheduleChangedAt은 판매 시작 뒤 대회 일시·장소를 바꾼 일시로, 이날부터 7일 동안 전액 환불이다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = RaceInternalApiExamples.COURSE_INFO_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "종목 ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceInternalApiExamples.INVALID_VALUE_TYPE))),
		@ApiResponse(responseCode = "404", description = "없는 종목",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceInternalApiExamples.COURSE_NOT_FOUND)))
	})
	public ResponseEntity<RsData<RaceCourseInfoDto>> findCourseInfo(
		@Parameter(description = "종목 ID", example = "1")
		@PathVariable long raceCourseId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "종목 정보를 조회했습니다.", productFacade.findCourseInfo(raceCourseId)));
	}
}
