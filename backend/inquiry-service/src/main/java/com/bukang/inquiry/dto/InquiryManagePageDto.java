package com.bukang.inquiry.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "판매자·관리자 문의 목록 한 페이지 (답변 대기 먼저, 그 안에서 최신순)")
public record InquiryManagePageDto(
	List<InquiryDto> inquiries,
	@Schema(description = "페이지 번호 (0부터)", example = "0") int page,
	@Schema(description = "페이지 크기", example = "20") int size,
	@Schema(description = "거른 결과의 전체 문의 수", example = "3") long totalElements,
	@Schema(description = "전체 페이지 수", example = "1") int totalPages,
	@Schema(description = "답변 대기 문의 수 (status로 거르기와 상관없이 이 화면 범위 전체)", example = "2") long waitingCount,
	@Schema(description = "답변 완료 문의 수 (status로 거르기와 상관없이 이 화면 범위 전체)", example = "1") long answeredCount
) {
	public static InquiryManagePageDto of(Page<InquiryDto> page, long waitingCount, long answeredCount) {
		return new InquiryManagePageDto(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
			page.getTotalPages(), waitingCount, answeredCount);
	}
}
