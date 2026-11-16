package com.bukang.ai.out.ops;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import com.bukang.ai.domain.AiScreen;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 운영 숫자를 각 서비스에서 읽어 온다 (도구가 쓴다). 읽기만 하므로 몇 번 불러도 된다
 * 회원 번호를 그대로 넘기고, 판매자·관리자 확인은 화면이 부를 때와 똑같이 그 서비스가 한다
 * 도구에 필요한 값만 받는다. 배송지·연락처·대표자 이름 같은 값은 응답에 있어도 읽지 않는다
 * 가져오지 못하면(연결 실패, 4xx·5xx, 빈 응답, 숫자 칸이 빠진 응답) RuntimeException을 던진다
 * 숫자 칸은 required로 받는다. 상대가 칸 이름을 바꿨을 때 0으로 읽혀 「0건」이라고 답하지 않게 한다
 */
public interface OpsPort {
	// 주문: 키트 배송 건수 (배송 준비, 그중 환불 기한 전이라 아직 보낼 수 없는 것)
	KitSummary kitSummary(long memberId);

	// 주문: 배송 준비 키트 목록의 앞 size개와 전체 건수
	KitPage preparingKits(long memberId, int size);

	// 주문: 결제일이 from~to(종료일 포함)인 결제 완료·취소 중 참가권의 장수·결제 금액 합과,
	// 취소를 마친 때가 그 기간인 참가권의 장수·환불액 합 (기간은 최대 12개월)
	SalesSummary salesSummary(long memberId, LocalDate from, LocalDate to);

	// 정산: 다음 정산일(없으면 null), 정산 예정액, 지급보류 건수
	PayoutSummary payoutSummary(long memberId);

	// 정산: 정산 월이 기간 안인 정산 내역 중 지금 지급보류인 것 (기간은 최대 12개월)
	List<HeldPayout> heldPayouts(long memberId, YearMonth fromMonth, YearMonth toMonth);

	// 정산: 보류한 날이 기간 안인 지급보류 (해제된 것 포함, 기간은 최대 1개월)
	List<Hold> holds(long memberId, LocalDate from, LocalDate to);

	// 문의: 답변 대기 문의 수. adminAssignedOnly면 관리자 담당만
	long waitingInquiryCount(long memberId, boolean adminAssignedOnly);

	// 회원: 승인 대기 판매자 신청의 신청 시각들 (이름·사업자 정보는 읽지 않는다)
	List<LocalDateTime> pendingSellerRequestedAts(long memberId);

	// 문의: 판매자·관리자 문의 상세 (문의 답변 초안이 쓴다). 화면 종류대로 판매자용·관리자용 상세를 부른다
	// 남의 대회 문의, 판매자 화면에서 연 관리자 담당 문의, 없거나 지운 문의는 문의 서비스가 404로 막고 여기서는 빈 값이다
	Optional<Inquiry> inquiry(long memberId, AiScreen screen, long inquiryId);

	@JsonIgnoreProperties(ignoreUnknown = true)
	record KitSummary(@JsonProperty(required = true) long preparing,
		@JsonProperty(required = true) long beforeRefundDeadline) {
	}

	record KitPage(List<Kit> kits, long totalElements) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Kit(String orderNo, String participantName, String courseName) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SalesSummary(@JsonProperty(required = true) long soldCount,
		@JsonProperty(required = true) long soldAmount, @JsonProperty(required = true) long canceledCount,
		@JsonProperty(required = true) long refundedAmount) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record PayoutSummary(LocalDate nextPayDate, @JsonProperty(required = true) long scheduledPayoutAmount,
		@JsonProperty(required = true) long heldCount) {
	}

	// settledAt: 정산 내역이 생긴 때. 보류는 그 뒤에만 걸린다
	record HeldPayout(String payoutCode, LocalDateTime settledAt) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	// releasedAt이 null이어도 대회 취소로 닫힌 정산 내역의 보류일 수 있다. 보류 중인지는 heldPayouts로 가린다
	record Hold(@JsonProperty(required = true) String payoutCode, String raceName,
		@JsonProperty(required = true) long payoutAmount, String reasonType, String reason,
		LocalDateTime heldAt, LocalDateTime releasedAt) {
	}

	// 회원 닉네임(memberNickname)은 읽지 않는다. order는 주문이 없거나 주문 서비스를 못 쓰면 null이다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Inquiry(@JsonProperty(required = true) String type, @JsonProperty(required = true) String title,
		@JsonProperty(required = true) String body, @JsonProperty(required = true) String status,
		LocalDateTime createDate, InquiryOrder order) {
	}

	// paymentAmount는 관리자 상세에만 온다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record InquiryOrder(String orderNo, Long paymentAmount, String status, List<InquiryOrderItem> items) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record InquiryOrderItem(String raceName, String courseName, String kitSummary, String deliveryStatus) {
	}
}
