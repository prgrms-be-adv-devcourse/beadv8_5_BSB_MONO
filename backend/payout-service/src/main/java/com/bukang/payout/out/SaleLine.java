package com.bukang.payout.out;

/**
 * 최종 대사가 비교하는 판매 줄의 값만 뽑은 것 (BUK-252)
 * 정산 내역의 원장 줄을 엔티티로 읽으면 참가권 10만 장에서 줄 20만 개가 영속성 컨텍스트에 올라 힙이 넘친다
 */
public record SaleLine(long orderItemId, long amount, Long raceCourseId, long payoutId) {
}
