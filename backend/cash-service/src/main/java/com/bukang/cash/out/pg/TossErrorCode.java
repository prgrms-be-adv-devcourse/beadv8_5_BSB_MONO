package com.bukang.cash.out.pg;

import java.util.Arrays;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Toss 결제 승인 거절 응답의 code 중 우리가 따로 다루는 값
 * 나머지(카드 한도 초과, 결제 시간 만료 등)는 OTHER로 받는다. 결과가 분명한 거절이라 Toss 문구를 그대로 보여 준다
 */
@Getter
@RequiredArgsConstructor
public enum TossErrorCode {
	// 거절 응답이지만 승인됐는지 알 수 없어 결제 조회로 확인해야 하는 코드: 이미 처리됨, 다른 요청이 처리 중
	ALREADY_PROCESSED_PAYMENT(true, false),
	ALREADY_PROCESSING_REQUEST(true, false),

	// 우리 쪽 키·연동 설정 문제라 사용자가 고칠 수 없고, Toss 문구엔 내부 정보만 있어 고정 문구로 바꾸는 코드
	UNAUTHORIZED_KEY(false, true),
	INVALID_API_KEY(false, true),
	INCORRECT_BASIC_AUTH_FORMAT(false, true),
	INVALID_REQUEST(false, true),
	FORBIDDEN_REQUEST(false, true),
	NOT_FOUND_TERMINAL_ID(false, true),
	INVALID_UNREGISTERED_SUBMALL(false, true),
	NOT_REGISTERED_BUSINESS(false, true),

	OTHER(false, false);

	private final boolean resultUnknown;
	private final boolean messageHidden;

	public static TossErrorCode from(String code) {
		return Arrays.stream(values())
			.filter(errorCode -> errorCode.name().equals(code))
			.findFirst()
			.orElse(OTHER);
	}
}
