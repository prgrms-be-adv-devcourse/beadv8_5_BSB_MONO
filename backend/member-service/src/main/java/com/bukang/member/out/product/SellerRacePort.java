package com.bukang.member.out.product;

/**
 * 판매자 탈퇴 전에 상품 서비스에 판매 중인 대회가 있는지 묻는다 (CRU-23)
 * 어떤 판매 상태까지 "판매 중"으로 볼지는 상품 서비스가 정한다
 * 묻지 못하면 WithdrawCheckFailedException(503)을 던진다
 */
public interface SellerRacePort {
	boolean hasOnSaleRace(long memberId);
}
