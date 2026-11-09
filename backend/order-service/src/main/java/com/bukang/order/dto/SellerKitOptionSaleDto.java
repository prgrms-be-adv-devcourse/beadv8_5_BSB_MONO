package com.bukang.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 판매자 키트 옵션별 판매 현황 한 줄 (11/09 결정). 준비 수량은 상품에서, 판매 수는 주문이 센 값이다
 *
 * @param kitOptionId 상품의 옵션 ID
 * @param name        옵션 이름 (예: T-shirt)
 * @param value       옵션 값 (예: L)
 * @param stock       준비 수량. null이면 맞춤 제작이라 품절이 없다
 * @param sold        판매 수 (결제한 참가권 중 취소되지 않은 것)
 * @param remaining   남은 수량 (준비 수량 - 판매 수, 0 아래면 0). 맞춤 제작이면 null
 */
@Schema(description = "키트 옵션별 판매 현황")
public record SellerKitOptionSaleDto(long kitOptionId, String name, String value, Integer stock, int sold,
	Integer remaining) {
	public static SellerKitOptionSaleDto of(long kitOptionId, String name, String value, Integer stock, int sold) {
		return new SellerKitOptionSaleDto(kitOptionId, name, value, stock, sold,
			stock == null ? null : Math.max(stock - sold, 0));
	}
}
