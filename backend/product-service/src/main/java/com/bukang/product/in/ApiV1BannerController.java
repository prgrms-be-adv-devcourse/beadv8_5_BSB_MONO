package com.bukang.product.in;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.product.app.ProductFacade;
import com.bukang.product.dto.RaceSummaryDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

// 홈 배너 (CRU-147). 로그인 없이 본다 (게이트웨이가 /api/v1/product/**의 GET을 공개로 둔다)
@Tag(name = "Banner", description = "홈 배너 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/product/banners")
public class ApiV1BannerController {
	private final ProductFacade productFacade;

	@GetMapping
	@Operation(summary = "홈 배너",
		description = "관리자가 고른 대회 중 지금 홈에 보이는 대회를 고른 순서대로 대회 목록과 같은 카드로 돌려준다. "
			+ "접수 마감·판매 중지·판매 종료·취소된 대회는 뺀다. 배너 이미지는 대표 이미지 보기 주소(imageUrl, 30분 뒤 만료)이고, "
			+ "대표 이미지를 받지 못하면 null이다. 고른 대회가 없으면 빈 목록이다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = BannerApiExamples.BANNERS_SUCCESS)))
	})
	public ResponseEntity<RsData<List<RaceSummaryDto>>> findBanners() {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "홈 배너를 조회했습니다.", productFacade.findBanners()));
	}
}
