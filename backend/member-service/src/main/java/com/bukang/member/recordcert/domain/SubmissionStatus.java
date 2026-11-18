package com.bukang.member.recordcert.domain;

// 제출 상태. 승인 · 승인 불가는 판매자 심사, 취소됨은 참가권 환불로 바뀐다
public enum SubmissionStatus {
	CHECKING,
	AUTO_APPROVED,
	REJECTED,
	NEEDS_REVIEW,
	APPROVED,
	DENIED,
	CANCELED
}
