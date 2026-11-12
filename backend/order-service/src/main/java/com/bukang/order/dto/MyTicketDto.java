package com.bukang.order.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.bukang.order.domain.KitDeliveryStatus;
import com.bukang.order.domain.OrderItemStatus;
import com.bukang.order.domain.OrderStatus;
import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.domain.ParticipationType;
import com.bukang.order.domain.PartyStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 내 참가권 목록의 한 줄 (BUK-181). 참가 신청 1건 = 1줄이고, 결제 전 신청과 주문이 아직 없는 파티원 신청도 나온다
 * 시안 M15-C 내 참가권, M20 주문 내역, M42 문의할 참가권 고르기가 이 목록 하나로 그린다
 */
public record MyTicketDto(
	@Schema(description = "참가 신청 ID. 상세는 GET /participations/{id}", example = "10") long participationId,
	@Schema(description = "신청한 시각", example = "2026-11-12T10:00:00") LocalDateTime createDate,
	@Schema(description = "대회 ID", example = "100") long raceId,
	@Schema(description = "종목 ID", example = "2") long raceCourseId,
	@Schema(description = "대회 이름. 상품 서비스에 묻지 못하면 null", example = "2026 서울 마라톤") String raceName,
	@Schema(description = "종목 이름. 상품 서비스에 묻지 못하면 주문 당시 이름, 주문도 없으면 null", example = "10K")
	String courseName,
	@Schema(description = "대회 일시. 상품 서비스에 묻지 못하면 주문 당시 값, 주문도 없으면 null",
		example = "2026-12-20T08:00:00") LocalDateTime raceDate,
	@Schema(description = "환불 기한 (그날까지). 상품 서비스에 묻지 못하면 null", example = "2026-12-10") LocalDate refundDeadline,
	@Schema(description = "참가자 이름", example = "김러너") String participantName,
	@Schema(description = "신청 상태 PENDING(확보 전) / APPLIED(결제 대기) / CONFIRMED(참가 확정) / "
		+ "RELEASED(미결제 해제) / FAILED(확보 실패) / CANCELED(취소)", example = "CONFIRMED") ParticipationStatus status,
	@Schema(description = "INDIVIDUAL(개인 신청) / PARTY(파티원 신청)", example = "INDIVIDUAL")
	ParticipationType participationType,
	@Schema(description = "결제 기한. 자리 확보 전이면 null", example = "2026-11-12T10:15:00") LocalDateTime paymentDueAt,
	@Schema(description = "주문 ID. 주문 전이면 null", example = "5") Long orderId,
	@Schema(description = "주문번호. 주문 전이면 null", example = "OD-20261112-0A1F") String orderNo,
	@Schema(description = "주문 상태 CREATED(결제 대기) / PAID(결제 완료) / PARTIAL_CANCELED(부분 취소) / CANCELED(취소) / "
		+ "EXPIRED(기한 만료). 결제하지 않아 만료되면 주문 상품은 ORDERED로 남으니 이 칸으로 가른다. 주문 전이면 null",
		example = "PAID") OrderStatus orderStatus,
	@Schema(description = "주문 상품 ID. 참가권 취소(POST /orders/{orderId}/items/{orderItemId}/cancel)에 쓴다. 주문 전이면 null",
		example = "7") Long orderItemId,
	@Schema(description = "주문 상품 상태 ORDERED(결제 대기) / PAID(결제 완료) / CANCELING(취소 중) / CANCELED(취소). "
		+ "주문 전이면 null", example = "PAID") OrderItemStatus orderItemStatus,
	@Schema(description = "결제 금액. 주문 전이면 null", example = "50000") Long amount,
	@Schema(description = "결제 때 고른 키트 옵션 (이름·값, 고른 순). 결제 전이거나 옵션이 없는 종목이면 빈 목록")
	List<KitOptionChoiceDto> kitOptions,
	@Schema(description = "키트 배송 상태 PREPARING(배송 준비) / SHIPPING(배송 중) / DELIVERED(배송 완료). "
		+ "결제 전이거나 키트가 없으면 null", example = "SHIPPING") KitDeliveryStatus kitDeliveryStatus,
	@Schema(description = "택배사. 발송 전이면 null", example = "CJ대한통운") String kitCarrier,
	@Schema(description = "송장 번호. 발송 전이면 null", example = "123456789012") String kitTrackingNumber,
	@Schema(description = "파티 ID. 개인 신청이면 null", example = "1") Long partyId,
	@Schema(description = "크루 ID. 크루 이름은 GET /api/v1/member/crews/me에서 찾는다. 개인 신청이면 null", example = "7")
	Long crewId,
	@Schema(description = "파티 상태 RECRUITING(모집 중) / PAYING(결제 진행 중) / PAID(결제 끝) / CLOSED(종료). "
		+ "개인 신청이면 null", example = "PAID") PartyStatus partyStatus,
	@Schema(description = "파티장 회원 ID. 개인 신청이면 null", example = "3") Long leaderMemberId,
	@Schema(description = "파티장 닉네임. 회원 서비스에 없으면 「탈퇴한 회원」, 회원 서비스에 묻지 못하면 null",
		example = "러너A") String leaderNickname
) {
}
