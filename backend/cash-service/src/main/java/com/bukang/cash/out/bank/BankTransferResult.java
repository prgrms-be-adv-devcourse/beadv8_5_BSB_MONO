package com.bukang.cash.out.bank;

// 은행 송금 결과. UNKNOWN은 은행이 처리 중이라고 답했거나 응답을 못 받은 경우라, 돈이 나갔을 수도 있다
// NOT_FOUND는 조회에서만 나온다: 은행에 그 송금 ID가 없다(요청이 은행에 닿지 않았거나 아직 기록 전)
public enum BankTransferResult {
	COMPLETED,
	FAILED,
	UNKNOWN,
	NOT_FOUND
}
