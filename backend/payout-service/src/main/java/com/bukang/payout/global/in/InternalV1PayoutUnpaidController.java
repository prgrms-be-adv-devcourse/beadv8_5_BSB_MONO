package com.bukang.payout.global.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.payout.app.PayoutUnpaidQueryUseCase;
import com.bukang.payout.dto.SellerUnpaidDto;

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
 * 회원 서비스가 판매자 탈퇴를 막을지 정할 때, 아직 받지 않은 정산금이 남았는지 물어본다
 * receivable(갚을 돈)과 방향이 반대다. 탈퇴 쪽 연결(회원 서비스 포트)은 회원 담당 몫이고, 정산은 이 API까지만 둔다
 */
@Tag(name = "PayoutInternal", description = "판매자 미수령 정산금 조회 API (서비스 간 내부 호출 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/payout")
public class InternalV1PayoutUnpaidController {
	private final PayoutUnpaidQueryUseCase payoutUnpaidQueryUseCase;

	@GetMapping("/sellers/{sellerId}/unpaid")
	@Operation(summary = "판매자 미수령 정산금 조회",
		description = "판매자가 아직 매듭짓지 않은 정산금이 있는지 돌려준다. 지급 완료(받음) · 취소(대회 취소로 지급 안 함)를 뺀 모든 상태를 센다. "
			+ "지급 중 · 지급 확인 필요 · 지급 불일치처럼 돈이 나갔는지 확정되지 않은 건도 포함한다. 회원 서비스가 판매자 탈퇴를 막을 때 부른다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (받지 않은 정산금이 있는지는 data.hasUnpaid)",
			content = @Content(examples = {
				@ExampleObject(name = "받지 않은 정산금 있음", value = PayoutInternalApiExamples.HAS_UNPAID),
				@ExampleObject(name = "받지 않은 정산금 없음", value = PayoutInternalApiExamples.NO_UNPAID)
			}))
	})
	public ResponseEntity<RsData<SellerUnpaidDto>> unpaid(
		@Parameter(description = "판매자 ID (정산 내역의 판매자 ID)", example = "1")
		@PathVariable long sellerId
	) {
		SellerUnpaidDto unpaid = payoutUnpaidQueryUseCase.findUnpaid(sellerId);
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "판매자 미수령 정산금을 조회했습니다.", unpaid));
	}
}
