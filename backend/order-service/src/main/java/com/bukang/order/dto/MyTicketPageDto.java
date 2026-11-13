package com.bukang.order.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

// 내 참가권 목록 한 페이지 (BUK-181). 주문 목록(OrderPageDto)과 같은 모양이다
public record MyTicketPageDto(
	List<MyTicketDto> tickets,
	@Schema(description = "페이지 번호 (0부터)", example = "0") int page,
	@Schema(description = "페이지 크기", example = "20") int size,
	@Schema(description = "전체 참가권 수", example = "3") long totalElements,
	@Schema(description = "전체 페이지 수", example = "1") int totalPages
) {
}
