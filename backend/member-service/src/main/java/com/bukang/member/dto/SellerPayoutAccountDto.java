package com.bukang.member.dto;

/**
 * 판매자 정산 계좌 등록 여부 (내부 API, BUK-50). 정산 서비스는 등록 여부만 받고 계좌번호 같은 개인정보는 받지 않는다
 *
 * @param registered 계좌실명조회를 통과한 정산 계좌가 있으면 true
 */
public record SellerPayoutAccountDto(boolean registered) {
}
