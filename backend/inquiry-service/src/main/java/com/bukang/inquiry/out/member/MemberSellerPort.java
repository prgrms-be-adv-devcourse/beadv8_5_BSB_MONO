package com.bukang.inquiry.out.member;

/**
 * 회원이 승인된 판매자인지 회원 서비스에 묻는다. 판매자가 관리자에게 보내는 문의를 만들 때 부른다
 * 읽기만 하므로 몇 번 불러도 된다. 판매자 신청이 없거나 승인되지 않았거나 탈퇴한 회원이면 false다
 * 연결하지 못하면 false가 아니라 InquiryExternalServiceUnavailableException(503)을 던진다
 */
public interface MemberSellerPort {
	boolean isApprovedSeller(long memberId);
}
