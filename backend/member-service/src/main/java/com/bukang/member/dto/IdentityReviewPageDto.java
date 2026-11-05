package com.bukang.member.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

// 페이지는 회원이 아니라 묶음 단위로 센다 (BE-038 결정 3). 모양은 order의 AdminOrderPageDto와 같다
@Schema(description = "확인이 필요한 계정 한 페이지")
public record IdentityReviewPageDto(
	@Schema(description = "회원 묶음 (가장 최근 가입자가 있는 묶음부터)")
	List<IdentityReviewGroupDto> groups,
	@Schema(description = "페이지 번호 (0부터)", example = "0")
	int page,
	@Schema(description = "페이지 크기 (묶음 수)", example = "20")
	int size,
	@Schema(description = "전체 묶음 수", example = "1")
	long totalElements,
	@Schema(description = "전체 페이지 수", example = "1")
	int totalPages
) {
}
