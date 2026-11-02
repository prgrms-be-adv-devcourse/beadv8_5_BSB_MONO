package com.bukang.member.out.order;

/**
 * 회원 탈퇴 전에 주문 서비스에 끝나지 않은 참가권 수를 묻는다 (BE-022)
 * 끝나지 않은 참가권 = 취소·환불되지 않았고 대회 날짜가 오늘 이후(오늘 포함)인 참가권. 판단은 주문 서비스가 한다
 * 묻지 못하면 WithdrawCheckFailedException(503)을 던진다
 */
public interface ActiveTicketPort {
	int countActiveTickets(long memberId);
}
