package com.bukang.payout.out;

import java.time.LocalDate;

/**
 * 정산 대상을 고를 때 상품 서비스에서 받는 대회 정보 (PRO-32, PRO-214)
 * 1차는 환불 마감일 다음 날, 최종은 대회일 다음 날 만든다. 정산 월은 각각 환불 마감일 · 대회일의 달이다
 * 대회일은 주문 상품에 결제 때 복사된 값이 아니라 상품 서비스의 현재 값이다
 * 대회 이름은 영수증처럼 정산 내역에 저장한다 (판매자 조회 때 상품 서비스를 부르지 않는다)
 */
public record RaceSchedule(int raceId, String raceName, LocalDate raceDate, LocalDate refundDeadline) {
}
