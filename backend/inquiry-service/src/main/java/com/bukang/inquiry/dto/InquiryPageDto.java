package com.bukang.inquiry.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import io.swagger.v3.oas.annotations.media.Schema;

// 문의 목록 한 페이지. Page를 그대로 내보내지 않고 필요한 값만 옮긴다 (주문 서비스 OrderPageDto와 같은 모양)
@Schema(description = "문의 목록 한 페이지 (최신순)")
public record InquiryPageDto(
	List<InquiryDto> inquiries,
	@Schema(description = "페이지 번호 (0부터)", example = "0") int page,
	@Schema(description = "페이지 크기", example = "20") int size,
	@Schema(description = "전체 문의 수", example = "1") long totalElements,
	@Schema(description = "전체 페이지 수", example = "1") int totalPages
) {
	public static InquiryPageDto from(Page<InquiryDto> page) {
		return new InquiryPageDto(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
			page.getTotalPages());
	}
}
