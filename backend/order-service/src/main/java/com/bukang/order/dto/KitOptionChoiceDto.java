package com.bukang.order.dto;

import com.bukang.order.domain.ParticipationKitOption;

// 참가 신청이 고른 키트 옵션 하나 (결제 때 남긴 사본). 예: T-shirt · L
public record KitOptionChoiceDto(long kitOptionId, String name, String value) {
	public static KitOptionChoiceDto from(ParticipationKitOption option) {
		return new KitOptionChoiceDto(option.getKitOptionId(), option.getOptionName(), option.getOptionValue());
	}
}
