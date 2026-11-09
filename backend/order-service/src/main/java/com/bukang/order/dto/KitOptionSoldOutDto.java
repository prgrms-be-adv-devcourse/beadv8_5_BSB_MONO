package com.bukang.order.dto;

import java.util.List;

// 키트 옵션 품절 409의 data. 판매 상한에 닿은 옵션을 모두 담는다
public record KitOptionSoldOutDto(List<KitOptionChoiceDto> soldOut) {
}
