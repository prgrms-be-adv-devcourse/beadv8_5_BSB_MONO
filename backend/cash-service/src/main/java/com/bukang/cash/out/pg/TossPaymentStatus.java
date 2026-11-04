package com.bukang.cash.out.pg;

import static com.bukang.cash.out.pg.PaymentLookupResult.Status.COMPLETED;
import static com.bukang.cash.out.pg.PaymentLookupResult.Status.NOT_COMPLETED;
import static com.bukang.cash.out.pg.PaymentLookupResult.Status.PENDING;

import java.util.Arrays;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Toss 결제 조회 응답의 status 값과, 각 값을 우리 기준(완료·승인 안 됨·아직 정해지지 않음) 중 무엇으로 볼지
 */
@Getter
@RequiredArgsConstructor
public enum TossPaymentStatus {
	// 결제를 만들었고 인증 전
	READY(PENDING),
	// 인증이 끝나 승인을 기다리거나 승인 처리 중
	IN_PROGRESS(PENDING),
	// 가상계좌 입금 대기
	WAITING_FOR_DEPOSIT(PENDING),
	// 승인 완료
	DONE(COMPLETED),
	// 승인 뒤 전액 취소
	CANCELED(NOT_COMPLETED),
	// 승인 뒤 일부 취소. 돈 일부가 빠진 채라 실패로 단정하지 않고 확인 중으로 남겨 사람이 보게 한다
	PARTIAL_CANCELED(PENDING),
	// 승인 실패
	ABORTED(NOT_COMPLETED),
	// 인증 뒤 승인하지 않아 만료
	EXPIRED(NOT_COMPLETED),
	// Toss가 새로 추가한 값처럼 모르는 값. 실패로 단정하지 않는다
	UNKNOWN(PENDING);

	private final PaymentLookupResult.Status lookupStatus;

	public static TossPaymentStatus from(String value) {
		return Arrays.stream(values())
			.filter(status -> status.name().equals(value))
			.findFirst()
			.orElse(UNKNOWN);
	}
}
