package com.bukang.payout.domain;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;
import com.bukang.payout.domain.exception.PayoutHoldConflictException;

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
	// 배치가 자동으로 건 보류의 보류한 회원 ID. 회원 ID는 1부터 시작하므로 실제 회원과 겹치지 않는다
	public static final long SYSTEM_MEMBER_ID = 0;

	private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

	// 지급보류 ID: HD-보류한 날-16진수 4자리 (예: HD-20261012-0A3F). 정산 ID(ST-)와 같은 방식이다
	// 끝 4자리를 PK에서 만들어 저장 뒤에 채우므로 NULL을 허용한다
	@Column(unique = true, length = 20)
	private String holdCode;

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

	// 보류한 회원 ID. 자동 보류는 SYSTEM_MEMBER_ID(0)를 넣는다
	@Column(nullable = false)
	private long heldBy;

	@Column(nullable = false)
	private LocalDateTime heldAt;

	private LocalDateTime releasedAt;

	// 보류를 푼 회원 ID. 돈이 나가게 되는 처리라 누가 풀었는지 남긴다. 보류 중에는 NULL
	private Long releasedBy;

	// 보류는 Payout을 통해서만 건다.
	PayoutHold(Payout payout, PayoutHoldReason reasonType, String reason, long heldBy, LocalDateTime heldAt) {
		this.payout = payout;
		this.reasonType = reasonType;
		this.reason = reason;
		this.heldBy = heldBy;
		this.heldAt = heldAt;
		this.status = PayoutHoldStatus.HELD;
	}

	// 저장해서 ID를 받은 뒤 부른다. 하루 65,536건을 넘지 않는 한 같은 날 같은 코드가 나오지 않는다
	public void assignHoldCode() {
		this.holdCode = "HD-%s-%04X".formatted(heldAt.format(CODE_DATE), getId() & 0xFFFF);
	}

	// 해제는 Payout.release를 통해서만 한다. 정산 내역 상태와 지급 예정일을 함께 바꿔야 하기 때문이다
	void markReleased(long releasedBy, LocalDateTime releasedAt) {
		if (status == PayoutHoldStatus.RELEASED) {
			throw new PayoutHoldConflictException("이미 해제된 지급보류입니다.");
		}
		this.status = PayoutHoldStatus.RELEASED;
		this.releasedBy = releasedBy;
		this.releasedAt = releasedAt;
	}
}
