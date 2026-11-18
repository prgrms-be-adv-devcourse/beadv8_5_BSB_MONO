package com.bukang.payout.out;

import java.util.Optional;

/**
 * 대회 ID로 그 대회 판매자의 회원 ID를 찾는다. 정산 보류를 판매자에게 알릴 때 쓴다
 * 정산 내역은 판매자 ID(판매자 신청서 ID)로 묶여 있지만 알림은 회원 ID로 보낸다. 회원 서비스에는 판매자 ID → 회원 ID API가 없어,
 * 주문 · 문의 서비스처럼 상품 서비스의 대회 판매자 조회를 쓴다. 대회의 판매자는 한 명이라 정산 내역의 판매자와 같다
 */
@FunctionalInterface
public interface RaceSellerPort {
	/**
	 * @return 판매자의 회원 ID, 없는 대회면 빈 값
	 * @throws RuntimeException 상품 서비스에 묻지 못함
	 */
	Optional<Long> findSellerMemberId(long raceId);
}
