package com.bukang.product.dto;

/**
 * 판매자의 끝나지 않은 대회 여부 (내부 API). 회원 서비스가 판매자 회원 탈퇴를 막을지 정할 때 쓴다
 * hasOnSaleRace: 판매 예정·판매 중·판매 중지 대회가 하나라도 있으면 true다
 * 판매자로 등록되지 않은 회원도 탈퇴를 막도록 true로 담는다
 */
public record SellerOnSaleRaceDto(boolean hasOnSaleRace) {
}
