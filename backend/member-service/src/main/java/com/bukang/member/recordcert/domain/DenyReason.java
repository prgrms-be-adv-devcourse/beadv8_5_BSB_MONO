package com.bukang.member.recordcert.domain;

// 판매자 심사의 승인 불가 사유. 정해진 목록에서 하나 고르고 메모는 선택 (review-spec 11/17 초안)
public enum DenyReason {
	NAME_MISMATCH,
	OTHER_RACE,
	NOT_NET_TIME,
	BLURRY,
	OTHER;

	public static final int MEMO_MAX_LENGTH = 200;
}
