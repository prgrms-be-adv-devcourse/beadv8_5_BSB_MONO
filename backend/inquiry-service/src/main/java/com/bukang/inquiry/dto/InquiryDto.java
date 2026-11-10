package com.bukang.inquiry.dto;

import java.time.LocalDateTime;

import com.bukang.inquiry.domain.Inquiry;
import com.bukang.inquiry.domain.InquiryAssignee;
import com.bukang.inquiry.domain.InquiryStatus;
import com.bukang.inquiry.domain.InquiryType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "문의 한 건. 답변이 없으면 answer·answeredAt은 null. 판매자 문의는 raceId·orderId가 null. "
	+ "memberNickname·order는 판매자·관리자 목록·상세에만 채운다")
public record InquiryDto(
	@Schema(description = "문의 ID", example = "12") long id,
	@Schema(description = "대회 ID (판매자 문의는 null)", example = "1", nullable = true) Long raceId,
	@Schema(description = "주문 ID (판매자 문의는 null)", example = "500", nullable = true) Long orderId,
	@Schema(description = "문의 종류", example = "RACE_VENUE") InquiryType type,
	@Schema(description = "담당. SELLER 판매자 / ADMIN 관리자", example = "SELLER") InquiryAssignee assignee,
	@Schema(description = "제목", example = "주차는 어디에 하나요") String title,
	@Schema(description = "본문", example = "대회장 근처 주차장이 있는지 알고 싶어요.") String body,
	@Schema(description = "상태. WAITING 답변 대기 / ANSWERED 답변 완료", example = "WAITING") InquiryStatus status,
	@Schema(description = "답변 내용", example = "정문 앞 공영주차장을 이용해 주세요.", nullable = true) String answer,
	@Schema(description = "답변한 시각", example = "2026-11-10T15:30:00", nullable = true) LocalDateTime answeredAt,
	@Schema(description = "문의한 시각", example = "2026-11-10T10:00:00") LocalDateTime createDate,
	@Schema(description = "문의한 회원 닉네임 (판매자·관리자 화면. 회원 서비스를 쓸 수 없으면 null)", example = "러너하나",
		nullable = true) String memberNickname,
	@Schema(description = "주문 정보 (판매자·관리자 상세. 주문 서비스를 쓸 수 없거나 판매자 문의면 null)",
		nullable = true) InquiryOrderDto order
) {
	public static InquiryDto from(Inquiry inquiry) {
		return new InquiryDto(inquiry.getId(), inquiry.getRaceId(), inquiry.getOrderId(), inquiry.getType(),
			inquiry.getAssignee(), inquiry.getTitle(), inquiry.getBody(), inquiry.getStatus(), inquiry.getAnswer(),
			inquiry.getAnsweredAt(), inquiry.getCreateDate(), null, null);
	}

	// 판매자·관리자 화면용: 닉네임과 주문 정보를 채운 사본
	public InquiryDto withManageInfo(String memberNickname, InquiryOrderDto order) {
		return new InquiryDto(id, raceId, orderId, type, assignee, title, body, status, answer, answeredAt,
			createDate, memberNickname, order);
	}
}
