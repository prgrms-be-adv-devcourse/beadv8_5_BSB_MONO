package com.bukang.order.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 관리자 주문 조회 한 페이지 (BUK-26). 모양은 내 주문 목록(OrderPageDto)과 같다
@AllArgsConstructor
@Getter
public class AdminOrderPageDto {
	private final List<AdminOrderSummaryDto> orders;
	// 0부터 시작
	private final int page;
	private final int size;
	private final long totalElements;
	private final int totalPages;

	public static AdminOrderPageDto from(Page<AdminOrderSummaryDto> page) {
		return new AdminOrderPageDto(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
			page.getTotalPages());
	}
}
