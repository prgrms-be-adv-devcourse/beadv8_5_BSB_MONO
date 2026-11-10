package com.bukang.cash.out.bank;

// 은행 송금 결과. UNKNOWN은 은행이 처리 중이라고 답했거나 응답을 못 받은 경우라, 돈이 나갔을 수도 있다
public enum BankTransferResult {
	COMPLETED,
	FAILED,
	UNKNOWN
}
