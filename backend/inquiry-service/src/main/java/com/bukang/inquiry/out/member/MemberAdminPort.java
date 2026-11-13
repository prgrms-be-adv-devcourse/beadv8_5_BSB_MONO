package com.bukang.inquiry.out.member;

/**
 * 관리자 API를 부른 회원이 관리자인지 회원 서비스에 묻는다
 * 게이트웨이는 토큰만 확인하고 역할은 보지 않으므로 문의 서비스가 직접 막는다. 판정은 회원 서비스 DB의 역할로 한다
 * 읽기만 하므로 몇 번 불러도 된다. 없거나 탈퇴한 회원은 관리자가 아닌 것으로 답한다
 * 연결하지 못하면 false가 아니라 InquiryExternalServiceUnavailableException(503)을 던진다
 * (모르는 경우를 관리자로 보면 회원 서비스가 멈춘 동안 누구나 관리자 화면을 볼 수 있다)
 */
public interface MemberAdminPort {
	boolean isAdmin(long memberId);
}
