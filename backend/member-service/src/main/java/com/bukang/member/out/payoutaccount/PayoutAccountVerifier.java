package com.bukang.member.out.payoutaccount;

/**
 * 정산 계좌가 신청자의 것인지 은행에 묻는다 (BUK-50). 운영은 오픈뱅킹 계좌실명조회, 키가 없는 개발 환경은 가짜 확인이다
 * DB 트랜잭션 밖에서 부른다. 외부 호출을 기다리는 동안 회원 행 락을 쥐지 않게 하려는 것이다
 */
public interface PayoutAccountVerifier {
	/**
	 * @return 은행이 돌려준 예금주 이름
	 * @throws com.bukang.member.domain.exception.SellerAccountNotVerifiedException 은행이 맞지 않다고 답했다
	 * @throws com.bukang.member.domain.exception.SellerAccountVerifyUnavailableException 오픈뱅킹에 닿지 못했다
	 */
	String verify(PayoutAccountInquiry inquiry);
}
