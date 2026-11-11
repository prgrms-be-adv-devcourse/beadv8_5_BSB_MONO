package com.bukang.ai.out.ops;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 운영 숫자를 각 서비스에서 읽어 온다 (도구가 쓴다). 읽기만 하므로 몇 번 불러도 된다
 * 회원 번호를 그대로 넘기고, 판매자·관리자 확인은 화면이 부를 때와 똑같이 그 서비스가 한다
 * 도구에 필요한 값만 받는다. 배송지·연락처·대표자 이름 같은 값은 응답에 있어도 읽지 않는다
 * 가져오지 못하면(연결 실패, 4xx·5xx, 빈 응답) RuntimeException을 던진다
 */
public interface OpsPort {
	// 주문: 키트 배송 건수 (배송 준비, 그중 환불 기한 전이라 아직 보낼 수 없는 것)
	KitSummary kitSummary(long memberId);

	// 주문: 배송 준비 키트 목록의 앞 size개와 전체 건수
	KitPage preparingKits(long memberId, int size);

	// 정산: 다음 정산일(없으면 null), 정산 예정액, 지급보류 건수
	PayoutSummary payoutSummary(long memberId);

	// 정산: 보류한 날이 기간 안인 지급보류 (해제된 것 포함, 기간은 최대 1개월)
	List<Hold> holds(long memberId, LocalDate from, LocalDate to);

	// 문의: 답변 대기 문의 수. adminAssignedOnly면 관리자 담당만
	long waitingInquiryCount(long memberId, boolean adminAssignedOnly);

	// 회원: 승인 대기 판매자 신청의 신청 시각들 (이름·사업자 정보는 읽지 않는다)
	List<LocalDateTime> pendingSellerRequestedAts(long memberId);

	@JsonIgnoreProperties(ignoreUnknown = true)
	record KitSummary(long preparing, long beforeRefundDeadline) {
	}

	record KitPage(List<Kit> kits, long totalElements) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Kit(String orderNo, String participantName, String courseName) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record PayoutSummary(LocalDate nextPayDate, long scheduledPayoutAmount, long heldCount) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Hold(String raceName, long payoutAmount, String reasonType, String reason, String status,
				LocalDateTime heldAt) {
	}
}
