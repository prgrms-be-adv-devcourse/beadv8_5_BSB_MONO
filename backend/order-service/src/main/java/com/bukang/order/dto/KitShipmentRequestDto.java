package com.bukang.order.dto;

import com.bukang.order.domain.Shipment;
import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "키트 발송 요청. 택배사와 송장 번호를 넣으면 배송 중이 된다")
@AllArgsConstructor
@Getter
public class KitShipmentRequestDto {
	@Schema(description = "택배사 ID. GET /api/v1/order/seller/kits/carriers 목록의 id만 받는다 (BUK-55)",
		example = "kr.cjlogistics")
	private final String carrierId;

	// shortcut: 옛 프론트가 보내는 택배사 이름도 받는다(목록의 name과 같으면 그 ID로 본다). 프론트가 carrierId를 보내면 이 칸을 지운다
	@Schema(description = "(옛 칸) 택배사 이름. carrierId가 없을 때만 쓴다", example = "CJ대한통운", deprecated = true)
	private final String carrier;

	@Schema(description = "송장 번호 (50자 이하)", example = "684912345678")
	@NotBlank(message = "송장 번호를 입력해주세요.")
	@Size(max = Shipment.MAX_TRACKING_NUMBER_LENGTH, message = "송장 번호는 50자 이하로 입력해주세요.")
	private final String trackingNumber;

	@JsonIgnore
	@AssertTrue(message = "택배사를 골라주세요.")
	public boolean isCarrierChosen() {
		return isPresent(carrierId) || isPresent(carrier);
	}

	private static boolean isPresent(String value) {
		return value != null && !value.isBlank();
	}
}
