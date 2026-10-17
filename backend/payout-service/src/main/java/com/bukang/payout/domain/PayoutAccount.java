package com.bukang.payout.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 원장 계정: 원장 줄 하나가 판매자 몫의 어느 부분을 늘리거나 줄였는지 (PRO-214)
 * 잔고는 따로 저장하지 않고, 판매자 × 대회의 같은 계정 줄을 더해 계산한다
 * 부호는 판매자 기준이다. 판매자에게 가면 +, 판매자에게서 오면 −
 */
@Getter
@RequiredArgsConstructor
public enum PayoutAccount {
	// 참가권이 팔려 판매자가 최종적으로 번 돈 (판매 +, 수수료 −)
	EARNING("번 돈"),
	// 미리 준 1차 정산금 중 판매자가 가져도 되는 몫. 최종에서 남은 잔고를 상계해 0이 된다
	ADVANCE("선금"),
	// 판매자가 플랫폼에 갚아야 할 돈(미수금). 회수 요청 때 늘고, 상계나 입금으로 0이 된다
	RECEIVABLE("갚을 돈");

	private final String korean;
}
