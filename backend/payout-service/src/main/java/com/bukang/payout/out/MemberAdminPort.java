package com.bukang.payout.out;

/**
 * 회원이 관리자인지 회원 서비스에 묻는 포트 (PRO-265)
 * 토큰에는 역할이 없고 게이트웨이도 역할을 보지 않으므로, 정산의 관리자 API가 직접 묻는다
 */
public interface MemberAdminPort {
	/**
	 * @return 관리자면 true, 일반 회원이면 false
	 * @throws com.bukang.payout.domain.exception.PayoutMemberNotFoundException 없거나 탈퇴한 회원
	 * @throws com.bukang.payout.domain.exception.PayoutMemberUnavailableException 회원 서비스가 응답하지 않음
	 */
	boolean isAdmin(long memberId);
}
