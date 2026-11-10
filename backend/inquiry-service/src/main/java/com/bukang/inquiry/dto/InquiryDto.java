package com.bukang.inquiry.dto;

import java.time.LocalDateTime;

import com.bukang.inquiry.domain.Inquiry;
import com.bukang.inquiry.domain.InquiryAssignee;
import com.bukang.inquiry.domain.InquiryStatus;
import com.bukang.inquiry.domain.InquiryType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "문의 한 건. 답변이 없으면 answer·answeredAt은 null")
public record InquiryDto(
	@Schema(description = "문의 ID", example = "12") long id,
	@Schema(description = "대회 ID", example = "1") long raceId,
	@Schema(description = "문의 종류", example = "RACE_VENUE") InquiryType type,
	@Schema(description = "담당. SELLER 판매자 / ADMIN 관리자", example = "SELLER") InquiryAssignee assignee,
	@Schema(description = "제목", example = "주차는 어디에 하나요") String title,
	@Schema(description = "본문", example = "대회장 근처 주차장이 있는지 알고 싶어요.") String body,
	@Schema(description = "상태. WAITING 답변 대기 / ANSWERED 답변 완료", example = "WAITING") InquiryStatus status,
	@Schema(description = "답변 내용", example = "정문 앞 공영주차장을 이용해 주세요.", nullable = true) String answer,
	@Schema(description = "답변한 시각", example = "2026-11-10T15:30:00", nullable = true) LocalDateTime answeredAt,
	@Schema(description = "문의한 시각", example = "2026-11-10T10:00:00") LocalDateTime createDate
) {
	public static InquiryDto from(Inquiry inquiry) {
		return new InquiryDto(inquiry.getId(), inquiry.getRaceId(), inquiry.getType(), inquiry.getAssignee(),
			inquiry.getTitle(), inquiry.getBody(), inquiry.getStatus(), inquiry.getAnswer(), inquiry.getAnsweredAt(),
			inquiry.getCreateDate());
	}
}
