package com.bukang.order.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 판매자 주문 목록 한 페이지 (BUK-227). SellerKitPageDto와 같은 모양
@AllArgsConstructor
@Getter
public class SellerOrderPageDto {
	private final List<SellerOrderDto> orders;
	// 0부터 시작
	private final int page;
	private final int size;
	private final long totalElements;
	private final int totalPages;

	public static SellerOrderPageDto from(Page<SellerOrderDto> page) {
		return new SellerOrderPageDto(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
			page.getTotalPages());
	}
}
