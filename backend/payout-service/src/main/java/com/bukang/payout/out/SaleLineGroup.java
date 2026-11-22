package com.bukang.payout.out;

/**
 * 최종 대사: 판매 줄을 (코스, 금액)으로 묶은 수 (CRU-57)
 * 판매 가격과 같은 묶음이면 그 줄들은 가격이 맞으므로 하나씩 읽지 않는다
 */
public record SaleLineGroup(Long raceCourseId, long amount, long lineCount) {
}
