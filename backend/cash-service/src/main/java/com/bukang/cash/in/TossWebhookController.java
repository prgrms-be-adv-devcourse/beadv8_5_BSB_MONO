package com.bukang.cash.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.cash.app.CashFacade;
import com.bukang.cash.dto.recovery.TossDepositCallbackRequestDto;
import com.bukang.common.global.rsdata.RsData;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 토스페이먼츠가 부르는 웹훅. 게이트웨이가 토큰 없이 통과시킨다(gateway.auth.public-paths)
 * 토스는 200을 받을 때까지 같은 알림을 다시 보내므로, 기록하지 않는 알림(비밀값 다름, 모르는 주문번호, 이미 처리함)에도 200으로 답한다
 */
@Tag(name = "TossWebhook", description = "토스 가상계좌 입금 알림 API (토스 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cash/webhooks/toss")
public class TossWebhookController {
	private final CashFacade cashFacade;

	@PostMapping("/deposit")
	@Operation(summary = "가상계좌 입금 알림", description = "판매자가 청구용 가상계좌에 입금했다는 토스 알림을 받는다. 비밀값이 맞으면 토스에 "
		+ "다시 조회해 입금 완료·금액을 확인하고 홀딩 지갑에 회수 금액을 한 번 기록한다. 기록하지 않는 알림에도 200으로 답한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "알림을 받음 (기록했거나, 기록할 필요가 없는 알림)",
			content = @Content(examples = @ExampleObject(value = CashApiExamples.TOSS_DEPOSIT_RECEIVED))),
		@ApiResponse(responseCode = "400", description = "요청 본문 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CashApiExamples.TOSS_DEPOSIT_INVALID_BODY)))
	})
	public ResponseEntity<RsData<Void>> receiveDeposit(@RequestBody TossDepositCallbackRequestDto request) {
		cashFacade.receiveRecoveryDeposit(request.getOrderId(), request.getSecret(), request.getStatus());
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "입금 알림을 받았습니다.", null));
	}
}
