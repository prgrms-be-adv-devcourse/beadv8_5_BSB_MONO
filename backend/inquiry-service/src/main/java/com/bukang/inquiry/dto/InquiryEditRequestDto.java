package com.bukang.inquiry.dto;

import com.bukang.inquiry.domain.Inquiry;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "문의 고치기 요청. 제목과 본문만 고친다")
public record InquiryEditRequestDto(
	@Schema(description = "제목 (100자 이하)", example = "주차는 어디에 하나요") @NotBlank(message = "제목을 입력해주세요.")
	@Size(max = Inquiry.MAX_TITLE_LENGTH, message = "제목은 100자 이하로 입력해주세요.") String title,

	@Schema(description = "본문 (1000자 이하)", example = "대회장 근처 주차장이 있는지 알고 싶어요.")
	@NotBlank(message = "문의 내용을 입력해주세요.")
	@Size(max = Inquiry.MAX_BODY_LENGTH, message = "문의 내용은 1000자 이하로 입력해주세요.") String body
) {
}
