package com.bukang.order.dto;

import com.bukang.order.domain.DeliveryCarrier;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "키트를 보낼 수 있는 택배사")
public record DeliveryCarrierDto(
	@Schema(description = "택배사 ID. 발송 요청의 carrierId에 넣는다", example = "kr.cjlogistics")
	String id,
	@Schema(description = "택배사 이름", example = "CJ대한통운")
	String name
) {
	public static DeliveryCarrierDto from(DeliveryCarrier carrier) {
		return new DeliveryCarrierDto(carrier.getId(), carrier.getLabel());
	}
}
