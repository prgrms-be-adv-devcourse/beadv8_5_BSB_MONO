package com.bukang.member.recordcert.domain;

// 판정 사유. 반려 사유가 하나라도 있으면 반려, 아니면 심사 필요 (판정 규칙 J2~J7)
public enum JudgementReason {
	BEFORE_RECOGNIZED_PERIOD(true),
	NET_TIME_OVER(true),
	COURSE_NOT_ALLOWED(true),
	COURSE_OTHER(false),
	FUTURE_RACE_DATE(false),
	TOO_FAST(false);

	private final boolean rejects;

	JudgementReason(boolean rejects) {
		this.rejects = rejects;
	}

	public boolean rejects() {
		return rejects;
	}
}
