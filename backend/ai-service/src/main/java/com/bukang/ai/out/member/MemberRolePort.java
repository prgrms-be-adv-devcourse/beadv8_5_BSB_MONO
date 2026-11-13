package com.bukang.ai.out.member;

/**
 * 챗봇을 부른 회원의 역할을 회원 서비스에 묻는다
 * 게이트웨이는 토큰만 확인하고 역할은 보지 않으므로 AI 서비스가 직접 막는다
 * 읽기만 하므로 몇 번 불러도 된다. 없거나 탈퇴한 회원은 역할이 없는 것으로 답한다
 * 연결하지 못하면 false가 아니라 AiExternalServiceUnavailableException(503)을 던진다
 */
public interface MemberRolePort {
	boolean isAdmin(long memberId);

	boolean isApprovedSeller(long memberId);
}
