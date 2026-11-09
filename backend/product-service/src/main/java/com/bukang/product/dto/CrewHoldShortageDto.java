package com.bukang.product.dto;

// 크루 자리가 모자란 종목 1개: 종목 ID, 이 파티에 필요한 수, 지금 남은 크루 자리 수
// 응답 shortages 배열의 한 줄이다 (파티 크루 자리 확보 요청서 형식)
public record CrewHoldShortageDto(long raceCourseId, int needed, int remaining) {
}
