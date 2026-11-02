package com.bukang.order.dto;

import com.bukang.order.domain.Shipment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "키트 발송 요청. 택배사와 송장 번호를 넣으면 배송 중이 된다")
@AllArgsConstructor
@Getter
public class KitShipmentRequestDto {
	@Schema(description = "택배사 (30자 이하)", example = "CJ대한통운")
	@NotBlank(message = "택배사를 입력해주세요.")
	@Size(max = Shipment.MAX_CARRIER_LENGTH, message = "택배사는 30자 이하로 입력해주세요.")
	private final String carrier;

	@Schema(description = "송장 번호 (50자 이하)", example = "684912345678")
	@NotBlank(message = "송장 번호를 입력해주세요.")
	@Size(max = Shipment.MAX_TRACKING_NUMBER_LENGTH, message = "송장 번호는 50자 이하로 입력해주세요.")
	private final String trackingNumber;
}
