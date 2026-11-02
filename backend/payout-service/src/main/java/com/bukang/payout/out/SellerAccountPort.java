package com.bukang.payout.out;

/**
 * 정산이 판매자 정보를 가진 서비스에서 받아야 하는 데이터의 계약 (포트)
 * 판매자 정산 계좌(은행명, 예금주, 계좌번호)는 판매자 등록 때 받는다 (payout.md 2-5)
 * 계좌를 가진 서비스가 생기면 이 인터페이스를 구현한 어댑터(HTTP 호출 또는 Kafka 복제본)를 붙인다. 배치 코드는 바뀌지 않는다
 */
@FunctionalInterface
public interface SellerAccountPort {
	/**
	 * 판매자가 정산 계좌를 등록했는지 돌려준다. 없으면 정산 내역을 만들 때 '정산 계좌 미등록'으로 보류한다
	 */
	boolean hasPayoutAccount(long sellerId);
}
