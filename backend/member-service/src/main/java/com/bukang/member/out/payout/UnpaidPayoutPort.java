package com.bukang.member.out.payout;

/**
 * 판매자 탈퇴 전에 정산 서비스에 아직 지급되지 않은 정산금이 있는지 묻는다 (CRU-23)
 * 어떤 정산 상태까지 "지급되지 않음"으로 볼지는 정산 서비스가 정한다. sellerId는 판매자 ID(Seller.id)다
 * 묻지 못하면 WithdrawCheckFailedException(503)을 던진다
 */
public interface UnpaidPayoutPort {
	boolean hasUnpaidPayout(long sellerId);
}
