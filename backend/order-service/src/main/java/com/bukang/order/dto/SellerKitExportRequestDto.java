package com.bukang.order.dto;

import com.bukang.order.domain.KitDeliveryStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "키트 배송 목록 내려받기 요청. 키트 배송 관리 목록과 같은 조건으로 거르고, 내려받는 사유를 남긴다")
@AllArgsConstructor
@Getter
public class SellerKitExportRequestDto {
	@Schema(description = "대회 ID (생략하면 모든 대회)", example = "1")
	private final Long raceId;

	@Schema(description = "배송 상태 (PREPARING 배송 준비 / SHIPPING 배송 중 / DELIVERED 배송 완료, 생략 가능)",
		example = "PREPARING")
	private final KitDeliveryStatus status;

	@Schema(description = "내려받는 사유 (200자 이하)", example = "택배사 일괄 접수")
	@NotBlank(message = "내려받는 사유를 입력해주세요.")
	@Size(max = 200, message = "사유는 200자 이하로 입력해주세요.")
	private final String reason;
}
