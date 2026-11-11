package com.bukang.inquiry.dto;

import com.bukang.inquiry.domain.Inquiry;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "문의 답변 요청. 답변은 한 번만 달 수 있다")
public record InquiryAnswerRequestDto(
	@Schema(description = "답변 내용 (2000자 이하)", example = "주차장은 정문 앞 공영주차장을 이용해 주세요.")
	@NotBlank(message = "답변 내용을 입력해주세요.")
	@Size(max = Inquiry.MAX_ANSWER_LENGTH, message = "답변 내용은 2000자 이하로 입력해주세요.")
	String content
) {
}
