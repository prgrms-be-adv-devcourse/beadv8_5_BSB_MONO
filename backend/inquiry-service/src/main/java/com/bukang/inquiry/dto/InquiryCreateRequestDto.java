package com.bukang.inquiry.dto;

import com.bukang.inquiry.domain.Inquiry;
import com.bukang.inquiry.domain.InquiryType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "문의 남기기 요청. 주문 ID는 받지 않는다")
public record InquiryCreateRequestDto(
	@Schema(description = "문의할 대회 ID", example = "1")
	@NotNull(message = "대회 ID를 입력해주세요.")
	@Positive(message = "대회 ID가 올바르지 않습니다.")
	Long raceId,

	@Schema(description = "문의 종류. APPLY_PAYMENT 신청·결제(관리자 담당) / RACE_VENUE 대회·현장(판매자 담당) / "
		+ "KIT_DELIVERY 기념품·배송(판매자 담당) / ETC 기타(관리자 담당)", example = "RACE_VENUE")
	@NotNull(message = "문의 종류를 선택해주세요.")
	InquiryType type,

	@Schema(description = "제목 (100자 이하)", example = "주차는 어디에 하나요")
	@NotBlank(message = "제목을 입력해주세요.")
	@Size(max = Inquiry.MAX_TITLE_LENGTH, message = "제목은 100자 이하로 입력해주세요.")
	String title,

	@Schema(description = "본문 (2000자 이하)", example = "대회장 근처 주차장이 있는지 알고 싶어요.")
	@NotBlank(message = "문의 내용을 입력해주세요.")
	@Size(max = Inquiry.MAX_BODY_LENGTH, message = "문의 내용은 2000자 이하로 입력해주세요.")
	String body
) {
}
