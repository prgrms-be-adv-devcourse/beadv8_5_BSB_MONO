package com.bukang.payout.global.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.payout.app.PayoutReceivableQueryUseCase;
import com.bukang.payout.dto.SellerReceivableDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 서비스끼리 부르는 내부 API. 게이트웨이에 라우팅하지 않는다(/internal/** 경로)
 * 회원 서비스가 판매자 탈퇴를 막을지 정할 때, 플랫폼에 돌려줄 정산금이 남았는지 물어본다 (선 환불 후 회수 R11)
 * 탈퇴 쪽 연결(회원 서비스 포트)은 회원 담당 몫이고, 정산은 이 API까지만 둔다
 */
@Tag(name = "PayoutInternal", description = "판매자 갚을 돈 조회 API (서비스 간 내부 호출 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/payout")
public class InternalV1PayoutReceivableController {
	private final PayoutReceivableQueryUseCase payoutReceivableQueryUseCase;

	@GetMapping("/sellers/{sellerId}/receivable")
	@Operation(summary = "판매자 갚을 돈 조회",
		description = "판매자가 대회 취소 · 일정 변경 환불로 플랫폼에 돌려줄 정산금(갚을 돈)이 남은 대회가 있는지 돌려준다. "
			+ "대회마다 원장 잔고를 따로 더해 0원보다 큰 대회만 센다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (갚을 돈이 있는지는 data.hasReceivable)",
			content = @Content(examples = {
				@ExampleObject(name = "갚을 돈 있음", value = PayoutInternalApiExamples.HAS_RECEIVABLE),
				@ExampleObject(name = "갚을 돈 없음", value = PayoutInternalApiExamples.NO_RECEIVABLE)
			}))
	})
	public ResponseEntity<RsData<SellerReceivableDto>> receivable(
		@Parameter(description = "판매자 ID (정산 내역의 판매자 ID)", example = "1")
		@PathVariable long sellerId
	) {
		SellerReceivableDto receivable = payoutReceivableQueryUseCase.findReceivable(sellerId);
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "판매자 갚을 돈을 조회했습니다.", receivable));
	}
}
