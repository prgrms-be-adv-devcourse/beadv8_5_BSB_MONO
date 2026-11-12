package com.bukang.cash.out.bank;

/**
 * 은행에 판매자 계좌 송금을 요청한다. 지금은 cash-service 안의 가짜 은행(fakebank)을 HTTP로 부른다
 * 은행은 같은 transferId를 한 번만 송금하므로, 결과를 모를 때 같은 ID로 다시 보내도 두 번 나가지 않는다.
 */
public interface BankTransferClient {
	BankTransferResult transfer(String transferId, long sellerId, long amount);

	// 결과를 모르는 송금을 재확인할 때 쓴다. 조회 자체가 실패하면 UNKNOWN이다
	BankTransferResult lookup(String transferId);
}
