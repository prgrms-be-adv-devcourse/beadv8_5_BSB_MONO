package com.bukang.board.out.member;

/**
 * 관리자 API를 부른 회원이 관리자인지 회원 서비스에 묻는다 (게이트웨이는 토큰만 확인하고 역할은 보지 않는다)
 * 없거나 탈퇴한 회원은 false다. 연결하지 못하면 false가 아니라 BoardExternalServiceUnavailableException(503)을 던진다
 * (모르는 경우를 관리자로 보면 회원 서비스가 멈춘 동안 누구나 관리자 화면을 쓸 수 있다)
 */
public interface MemberAdminPort {
	boolean isAdmin(long memberId);
}
