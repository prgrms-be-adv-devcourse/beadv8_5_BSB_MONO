package com.bukang.member.out.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 상품 서비스 「결제율 미달 크루 조회」 응답의 data 원소. 제한 저장에 쓰는 값만 받는다 (BUK-187)
@JsonIgnoreProperties(ignoreUnknown = true)
public record LowPaymentCrew(Long crewId, Integer lowPaymentCount) {
}
