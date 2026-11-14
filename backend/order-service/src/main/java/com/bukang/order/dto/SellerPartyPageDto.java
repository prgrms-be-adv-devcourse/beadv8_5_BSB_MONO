package com.bukang.order.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 판매자 파티별 내역 한 페이지 (BUK-227)
@AllArgsConstructor
@Getter
public class SellerPartyPageDto {
	private final List<SellerPartyDto> parties;
	// 0부터 시작
	private final int page;
	private final int size;
	private final long totalElements;
	private final int totalPages;

	public static SellerPartyPageDto from(Page<SellerPartyDto> page) {
		return new SellerPartyPageDto(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
			page.getTotalPages());
	}
}
