package com.bukang.payout.out;

import java.time.LocalDate;

/**
 * 정산할 참가권 1장 (결제되고 취소되지 않은 주문 상품)
 * 주문 서비스의 order_item에서 정산에 필요한 값만 옮긴 것이다
 * 참가권 order_item은 항상 quantity = 1이므로 price는 참가권 1장 값이고, 정산 항목 1개 = 참가권 1장이다
 * raceDate는 정산 대상일과 정산 월을 대회일에서 계산하기 위해 받는다 (기간으로 조회하므로 참가권마다 대회일이 다르다)
 */
// raceCourseId: 참가권의 코스. 원장 판매 · 수수료 줄에 적어 판매자가 코스별로 본다 (PRO-214). 주문 서비스가 아직 안 보내면 NULL
public record SoldEntry(int orderItemId, int sellerId, int raceId, Integer raceCourseId, LocalDate raceDate,
	long price) {
}
