package com.bukang.cash.dto.recovery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

// 토스 가상계좌 입금 알림(DEPOSIT_CALLBACK) 본문 중 우리가 쓰는 값만 받는다
// 입금 완료와 금액은 이 본문을 믿지 않고 토스에 다시 조회한다. status는 입금 오류를 알아채는 데만 쓴다
@Schema(description = "토스 가상계좌 입금 알림")
@AllArgsConstructor
@Getter
public class TossDepositCallbackRequestDto {
	@Schema(description = "가상계좌를 발급할 때 쓴 주문번호 (= 정산의 청구 ID)", example = "RC-20261214-0A3F")
	private final String orderId;

	@Schema(description = "가상계좌를 발급할 때 토스가 준 비밀값. 진짜 토스 알림인지 맞춰 본다", example = "ps_secret_1")
	private final String secret;

	@Schema(description = "결제 상태. 입금되면 DONE, 입금 오류면 WAITING_FOR_DEPOSIT(옛 버전은 CANCELED)", example = "DONE")
	private final String status;
}
