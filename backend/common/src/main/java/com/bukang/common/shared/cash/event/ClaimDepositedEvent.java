package com.bukang.common.shared.cash.event;

import java.time.LocalDateTime;

/**
 * 청구 입금 알림 (BUK-88, 계약 초안: 캐시 담당과 맞추는 중)
 * 판매자가 청구 전용 가상계좌에 입금하면 캐시 서비스가 입금을 확인한 뒤 보낸다 (BUK-84).
 * 정산 서비스가 받아 청구를 입금 확인으로 바꾸고 회수를 끝낸다. 청구 ID를 key로 보낸다.
 *
 * @param claimCode 정산의 청구 ID (예: RC-20261214-0A3F). 가상계좌를 발급할 때 토스 orderId로 쓴 값
 * @param amount 입금된 금액. 청구 금액과 다르면 정산은 회수를 끝내지 않는다
 * @param depositedAt 입금된 시각 (한국 시간)
 */
public record ClaimDepositedEvent(
	String claimCode,
	long amount,
	LocalDateTime depositedAt
) {
}
