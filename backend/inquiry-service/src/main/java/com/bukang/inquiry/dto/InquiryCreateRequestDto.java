package com.bukang.inquiry.dto;

import com.bukang.inquiry.domain.Inquiry;
import com.bukang.inquiry.domain.InquiryType;
import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "문의 남기기 요청. 판매자 담당 종류(RACE_VENUE, KIT_DELIVERY)는 대회 ID가 필수이고 주문 ID는 선택이다. "
	+ "관리자 담당 종류(APPLY_PAYMENT, ETC)는 대회 ID·주문 ID가 모두 선택이다. 주문 ID를 보내면 대회 ID도 함께 보낸다. "
	+ "판매자 문의(SELLER_SUPPORT)는 둘 다 보내지 않는다")
public record InquiryCreateRequestDto(
	@Schema(description = "문의할 대회 ID. 주문 ID를 함께 보내면 그 주문에 있는 대회여야 한다 "
		+ "(판매자 담당 종류는 필수, 판매자 문의는 null)", example = "1", nullable = true)
	@Positive(message = "대회 ID가 올바르지 않습니다.")
	Long raceId,

	@Schema(description = "문의할 주문 ID. 보내면 내 주문이어야 한다 (선택, 판매자 문의는 null)", example = "500", nullable = true)
	@Positive(message = "주문 ID가 올바르지 않습니다.")
	Long orderId,

	@Schema(description = "문의 종류. APPLY_PAYMENT 신청·결제(관리자 담당) / RACE_VENUE 대회·현장(판매자 담당) / "
		+ "KIT_DELIVERY 기념품·배송(판매자 담당) / ETC 기타(관리자 담당) / "
		+ "SELLER_SUPPORT 판매자 문의(승인된 판매자가 관리자에게)", example = "RACE_VENUE")
	@NotNull(message = "문의 종류를 선택해주세요.")
	InquiryType type,

	@Schema(description = "제목 (100자 이하)", example = "주차는 어디에 하나요")
	@NotBlank(message = "제목을 입력해주세요.")
	@Size(max = Inquiry.MAX_TITLE_LENGTH, message = "제목은 100자 이하로 입력해주세요.")
	String title,

	@Schema(description = "본문 (1000자 이하)", example = "대회장 근처 주차장이 있는지 알고 싶어요.")
	@NotBlank(message = "문의 내용을 입력해주세요.")
	@Size(max = Inquiry.MAX_BODY_LENGTH, message = "문의 내용은 1000자 이하로 입력해주세요.")
	String body
) {
	@JsonIgnore
	@AssertTrue(message = "문의할 대회를 골라주세요.")
	public boolean isRaceChosenForSellerType() {
		return type == null || !type.requiresRace() || raceId != null;
	}

	@JsonIgnore
	@AssertTrue(message = "주문을 고르면 대회도 함께 보내주세요.")
	public boolean isRaceSentWithOrder() {
		return orderId == null || raceId != null;
	}

	@JsonIgnore
	@AssertTrue(message = "판매자 문의는 주문과 대회 없이 보내주세요.")
	public boolean isNotOrderLinked() {
		return type == null || type.linkedToOrder() || (raceId == null && orderId == null);
	}
}
