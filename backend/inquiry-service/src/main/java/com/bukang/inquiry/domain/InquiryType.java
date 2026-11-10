package com.bukang.inquiry.domain;

/**
 * 문의 종류. 종류로 담당이 자동으로 정해진다
 * 대회 운영과 키트는 판매자만 답할 수 있고, 결제·취소는 플랫폼 규칙이라 관리자가 답한다
 */
public enum InquiryType {
	// 신청·결제
	APPLY_PAYMENT(InquiryAssignee.ADMIN),
	// 대회·현장
	RACE_VENUE(InquiryAssignee.SELLER),
	// 기념품·배송
	KIT_DELIVERY(InquiryAssignee.SELLER),
	// 기타
	ETC(InquiryAssignee.ADMIN);

	private final InquiryAssignee assignee;

	InquiryType(InquiryAssignee assignee) {
		this.assignee = assignee;
	}

	public InquiryAssignee assignee() {
		return assignee;
	}
}
