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
	ETC(InquiryAssignee.ADMIN),
	// 판매자 문의: 승인된 판매자가 관리자에게 보낸다. 주문·대회 없이 보낸다
	SELLER_SUPPORT(InquiryAssignee.ADMIN);

	private final InquiryAssignee assignee;

	InquiryType(InquiryAssignee assignee) {
		this.assignee = assignee;
	}

	public InquiryAssignee assignee() {
		return assignee;
	}

	// 회원 문의는 대회(와 주문)에 묶을 수 있고, 판매자 문의는 대회·주문 없이 보낸다
	public boolean linkedToOrder() {
		return this != SELLER_SUPPORT;
	}

	// 판매자 담당 종류는 판매자를 정해야 하므로 대회 ID가 꼭 있어야 한다. 관리자 담당 종류는 대회가 없어도 된다
	public boolean requiresRace() {
		return assignee == InquiryAssignee.SELLER;
	}
}
