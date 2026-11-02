package com.bukang.payout.domain;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 정산 내역 상태 이력: 상태가 바뀔 때마다 한 줄씩 남긴다 (PRO-34)
 * 보류 중이던 정산 내역을 대회 취소로 닫으면 상태는 최신(CANCELED)만 남으므로, 그 전에 무엇이었는지를 여기서 본다
 * 추가만 하고 고치지 않는다. Payout을 통해서만 만든다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(name = "PAYOUT_PAYOUT_STATUS_HISTORY")
public class PayoutStatusHistory extends BaseIdAndTime {
	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "payout_id", nullable = false)
	private Payout payout;

	// 바뀌기 전 상태. 정산 내역을 처음 만들 때는 NULL
	@Enumerated(STRING)
	@Column(length = 20)
	private PayoutStatus fromStatus;

	@Enumerated(STRING)
	@Column(nullable = false, length = 20)
	private PayoutStatus toStatus;

	// 바꾼 회원 ID. 배치 · 이벤트가 바꾸면 PayoutHold.SYSTEM_MEMBER_ID(0)
	@Column(nullable = false)
	private long changedBy;

	@Column(nullable = false)
	private LocalDateTime changedAt;

	// 바꾼 이유 (예: 대회 취소). 없으면 NULL
	@Column(length = 200)
	private String reason;

	PayoutStatusHistory(Payout payout, PayoutStatus fromStatus, PayoutStatus toStatus, long changedBy,
		LocalDateTime changedAt, String reason) {
		this.payout = payout;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.changedBy = changedBy;
		this.changedAt = changedAt;
		this.reason = reason;
	}
}
