package com.bukang.member.out.payoutaccount;

/**
 * 회원 한 명의 계좌 확인 횟수를 센다 (BE-031 결정 5). 운영은 Redis, 테스트는 메모리 구현이다
 * 오픈뱅킹을 부르기 전에 센다. 남의 계좌번호로 예금주 이름을 알아내는 데 쓰이지 않게 하려는 것이다
 */
public interface PayoutAccountVerifyLimiter {
	// 한 번을 센다. 기간 안에서 한도를 넘으면 거짓
	boolean tryAcquire(long memberId);
}
