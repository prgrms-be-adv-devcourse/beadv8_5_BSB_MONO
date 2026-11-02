package com.bukang.member.out.cash;

/**
 * 회원 탈퇴 전에 예치금 서비스에 남은 잔액을 묻는다 (BE-022)
 * 묻지 못하면 WithdrawCheckFailedException(503)을 던진다. 잔액을 모르면 탈퇴하지 않는다
 */
public interface CashBalancePort {
	long findBalance(long memberId);
}
