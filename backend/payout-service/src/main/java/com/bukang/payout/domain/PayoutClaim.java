package com.bukang.payout.domain;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;
import com.bukang.payout.domain.exception.PayoutClaimConflictException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 청구: 상계로도 못 갚은 돈을 판매자 × 대회 하나에 대해 "이 금액을 이 계좌로 보내 주세요"라고 요청한 한 건
 * (선 환불 후 회수 R3 · R4, 피그잼 06-1 [6. 판매자 정산 / 3-1 ~ 3])
 * 정산 · 갚을 돈 · 상계가 모두 판매자 × 대회 단위라 청구도 같은 단위로 하나만 둔다
 * 최종 정산을 만든 뒤(대회 취소는 다음 날)에만 만들어, 만든 뒤에는 금액이 바뀌지 않는다
 * 돈의 기록은 원장 줄(회수 입금)이 맡고, 이 엔티티는 알림과 확인 절차만 맡는다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(name = "PAYOUT_PAYOUT_CLAIM",
	uniqueConstraints = @UniqueConstraint(name = "uk_payout_claim_seller_race", columnNames = {"seller_id", "race_id"}))
public class PayoutClaim extends BaseIdAndTime {
	// 관리자 알림을 남기는 날: 상계 실패일을 1일째로 셀 때 30일째 (피그잼 [6 / 3-3])
	private static final int ADMIN_NOTICE_DAY = 30;

	@Column(nullable = false)
	private long sellerId;

	@Column(nullable = false)
	private long raceId;

	// 판매자에게 요청한 금액 (청구를 만들 때의 갚을 돈)
	@Column(nullable = false)
	private long amount;

	// 상계 실패일. 최종 정산을 만든 날, 대회 취소는 취소일. 관리자 알림 날짜를 이 날부터 센다
	@Column(nullable = false)
	private LocalDate startDate;

	@Enumerated(STRING)
	@Column(nullable = false, length = 20)
	private PayoutClaimStatus status;

	// 마지막으로 판매자 청구 알림을 남긴 날. 같은 날 배치가 다시 돌아도 알림을 한 번만 남긴다
	private LocalDate sellerNotifiedDate;

	// 관리자 알림을 남긴 시각. 한 번만 남긴다
	private LocalDateTime adminNotifiedAt;

	private LocalDateTime confirmedAt;

	// 입금을 확인한 관리자 회원 ID
	private Long confirmedBy;

	public PayoutClaim(long sellerId, long raceId, long amount, LocalDate startDate) {
		if (amount <= 0) {
			throw new IllegalArgumentException("청구액은 0보다 커야 합니다. amount=%d".formatted(amount));
		}
		this.sellerId = sellerId;
		this.raceId = raceId;
		this.amount = amount;
		this.startDate = startDate;
		this.status = PayoutClaimStatus.OPEN;
	}

	// 오늘 판매자 청구 알림을 남겨야 하는지. 남긴다고 보고 날짜를 적는다
	public boolean markSellerNotice(LocalDate today) {
		if (status != PayoutClaimStatus.OPEN || today.equals(sellerNotifiedDate)) {
			return false;
		}
		this.sellerNotifiedDate = today;
		return true;
	}

	// 오늘 관리자 알림을 남겨야 하는지. 30일째부터 한 번만 남긴다
	public boolean markAdminNotice(LocalDate today, LocalDateTime now) {
		if (status != PayoutClaimStatus.OPEN || adminNotifiedAt != null) {
			return false;
		}
		if (today.isBefore(startDate.plusDays(ADMIN_NOTICE_DAY - 1))) {
			return false;
		}
		this.adminNotifiedAt = now;
		return true;
	}

	// 관리자가 판매자 입금을 확인했다
	public void confirm(long confirmedBy, LocalDateTime confirmedAt) {
		if (status != PayoutClaimStatus.OPEN) {
			throw new PayoutClaimConflictException("이미 입금을 확인한 청구입니다.");
		}
		this.status = PayoutClaimStatus.CONFIRMED;
		this.confirmedBy = confirmedBy;
		this.confirmedAt = confirmedAt;
	}
}
