package com.bukang.cash.out.member;

/**
 * 관리자 API를 부른 회원이 관리자인지 회원 서비스에 묻는다
 * 게이트웨이는 회원 ID만 넘기고 역할은 넘기지 않으며, 캐시의 회원 복제본에도 역할이 없어 회원 서비스에 직접 묻는다
 * 없거나 탈퇴한 회원은 관리자가 아닌 것으로 답한다
 * 연결하지 못하면 false가 아니라 MemberServiceUnavailableException(503)을 던진다
 * (모르는 경우를 관리자로 보면 회원 서비스가 멈춘 동안 누구나 관리자 화면을 볼 수 있다)
 */
public interface MemberAdminPort {
	boolean isAdmin(long memberId);
}
