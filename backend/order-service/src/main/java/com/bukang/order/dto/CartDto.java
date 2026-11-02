package com.bukang.order.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class CartDto {
	private final long memberId;
	// 최근에 담은 항목부터. 장바구니가 아직 없으면 빈 목록
	private final List<CartItemDto> items;
}
