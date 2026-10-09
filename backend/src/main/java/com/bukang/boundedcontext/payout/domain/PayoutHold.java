package com.bukang.boundedcontext.payout.domain;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import com.bukang.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지급보류: 정산 내역의 지급을 막는다. 보류 중인 정산 내역은 지급 완료로 바꿀 수 없다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(name = "PAYOUT_PAYOUT_HOLD")
public class PayoutHold extends BaseIdAndTime {
	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "payout_id", nullable = false)
	private Payout payout;

	@Enumerated(STRING)
	@Column(nullable = false, length = 30)
	private PayoutHoldReason reasonType;

	// 사유 상세 (선택)
	@Column(length = 500)
	private String reason;

	@Enumerated(STRING)
	@Column(nullable = false, length = 20)
	private PayoutHoldStatus status;

	// 보류한 회원 ID. 자동 보류는 system 회원 ID를 넣는다
	@Column(nullable = false)
	private int heldBy;

	@Column(nullable = false)
	private LocalDateTime heldAt;

	private LocalDateTime releasedAt;

	// 보류는 Payout을 통해서만 건다.
	PayoutHold(Payout payout, PayoutHoldReason reasonType, String reason, int heldBy, LocalDateTime heldAt) {
		this.payout = payout;
		this.reasonType = reasonType;
		this.reason = reason;
		this.heldBy = heldBy;
		this.heldAt = heldAt;
		this.status = PayoutHoldStatus.HELD;
	}
}
