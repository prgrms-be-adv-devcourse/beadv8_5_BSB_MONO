package com.bukang.payout.domain;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;
import com.bukang.payout.domain.exception.PayoutRecoveryConflictException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회수: 판매자에게 주었거나 주기로 한 1차 정산금 중 환불된 참가권 몫을 돌려받는 절차 (payout.md 4-2, PRO-33)
 * 돈의 기록은 원장 줄(요청 · 상계 · 입금)이 맡고, 이 엔티티는 절차(요청 · 완료 상태, 완료 방식)만 맡는다 (PRO-214)
 * - 대회 취소: 환불된 참가권마다 1건, 금액은 그 참가권의 1차 몫 (BUK-76. 예전 기록은 대회당 1건, 남은 1차 정산금)
 * - 일정 변경: 환불된 참가권마다 1건, 금액은 그 참가권의 1차 몫(가격 × 40%, 원 미만 버림)
 * 같은 판매자 × 대회의 갚을 돈이 0이 될 때(상계 또는 입금) 그 대회의 요청 중인 회수를 함께 완료한다
 * 회수가 끝나야 구매자 환불이 나간다 (정책 원문)
 * 판매자, 대회, 주문 상품은 다른 도메인이라 연관관계 없이 ID만 둔다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(name = "PAYOUT_PAYOUT_RECOVERY")
public class PayoutRecovery extends BaseIdAndTime {
	@Column(nullable = false)
	private long sellerId;

	@Column(nullable = false)
	private long raceId;

	// 회수 중복 방지 키. 참가권당 1건(O{주문 상품ID}:{사유}). 예전 대회 취소 회수는 대회당 1건(R{대회ID}:대회_취소)이었다 (BUK-76)
	// 같은 메시지를 두 곳이 동시에 처리해 앱의 확인을 함께 통과해도 DB가 두 번째 저장을 막는다 (PRO-34 리뷰)
	// (race_id, reason_type) 유일 조건은 일정 변경 회수를 대회당 1건으로 막아 이 키로 바꿨다 (PRO-214)
	@Column(nullable = false, unique = true, length = 50, updatable = false)
	private String recoveryKey;

	// 환불된 참가권(주문 상품). 같은 참가권으로 회수가 두 번 생기지 않게 유일하다. 예전 대회당 1건 대회 취소 회수만 NULL
	@Column(unique = true)
	private Long orderItemId;

	@Enumerated(STRING)
	@Column(nullable = false, length = 30)
	private PayoutRecoveryReason reasonType;

	// 요청한 금액. 화면 표시용이고 금액 계산은 원장 줄로 한다
	@Column(nullable = false)
	private long amount;

	// DB는 ENUM 칸이다. 상태를 더하면 같은 PR에 ENUM 목록을 바꾸는 Flyway SQL을 넣는다 (PayoutEnumColumnTest가 확인)
	@Enumerated(STRING)
	@Column(nullable = false, length = 20)
	private PayoutRecoveryStatus status;

	@Column(nullable = false)
	private LocalDateTime requestedAt;

	private LocalDateTime completedAt;

	// 입금을 확인한 회원 ID. 상계로 끝나면 시스템(0). 요청 중에는 NULL
	private Long completedBy;

	// 어떻게 끝났는지 (입금 / 상계). 요청 중에는 NULL
	@Enumerated(STRING)
	@Column(length = 20)
	private PayoutRecoveryCompletion completion;

	public PayoutRecovery(long sellerId, long raceId, PayoutRecoveryReason reasonType, long amount,
		LocalDateTime requestedAt) {
		this(sellerId, raceId, null, reasonType, amount, requestedAt);
	}

	private PayoutRecovery(long sellerId, long raceId, Long orderItemId, PayoutRecoveryReason reasonType, long amount,
		LocalDateTime requestedAt) {
		if (amount <= 0) {
			throw new IllegalArgumentException("회수액은 0보다 커야 합니다. amount=%d".formatted(amount));
		}
		this.sellerId = sellerId;
		this.raceId = raceId;
		this.orderItemId = orderItemId;
		this.reasonType = reasonType;
		this.recoveryKey = recoveryKeyOf(raceId, orderItemId, reasonType);
		this.amount = amount;
		this.requestedAt = requestedAt;
		this.status = PayoutRecoveryStatus.회수_요청;
	}

	// 일정 변경으로 환불된 참가권 한 장의 회수
	public static PayoutRecovery scheduleChanged(long sellerId, long raceId, long orderItemId, long amount,
		LocalDateTime requestedAt) {
		return new PayoutRecovery(sellerId, raceId, orderItemId, PayoutRecoveryReason.일정_변경, amount,
			requestedAt);
	}

	// 대회 취소로 환불된 참가권 한 장의 회수 (BUK-76)
	public static PayoutRecovery raceCanceled(long sellerId, long raceId, long orderItemId, long amount,
		LocalDateTime requestedAt) {
		return new PayoutRecovery(sellerId, raceId, orderItemId, PayoutRecoveryReason.대회_취소, amount,
			requestedAt);
	}

	private static String recoveryKeyOf(long raceId, Long orderItemId, PayoutRecoveryReason reasonType) {
		if (orderItemId != null) {
			return "O%d:%s".formatted(orderItemId, reasonType);
		}
		if (reasonType == PayoutRecoveryReason.일정_변경) {
			throw new IllegalArgumentException("일정 변경 회수에는 참가권(주문 상품) ID가 있어야 합니다.");
		}
		return "R%d:%s".formatted(raceId, reasonType);
	}

	// 판매자의 입금을 확인했다
	public void complete(long completedBy, LocalDateTime completedAt) {
		finish(PayoutRecoveryCompletion.입금, completedBy, completedAt);
	}

	// 지급 전 정산 내역에서 빼서 갚을 돈이 0이 됐다
	public void completeByOffset(LocalDateTime completedAt) {
		finish(PayoutRecoveryCompletion.상계, PayoutHold.SYSTEM_MEMBER_ID, completedAt);
	}

	// 1차 정산이 돈 없이 닫혀(대회 취소) 되돌렸다. 판매자가 그 몫을 받은 적이 없다 (R9, BUK-76)
	// 요청 중이거나 상계로 끝난 회수만 되돌린다. 입금으로 끝난 회수는 판매자가 낸 돈이라 되돌리지 않는다
	// 상계로 끝났으면 완료 방식 · 완료 시각은 그 기록으로 그대로 둔다
	public void cancelByRaceCancel() {
		if (!isRevertibleByRaceCancel()) {
			throw new IllegalStateException("요청 중이거나 상계로 끝난 회수만 대회 취소로 되돌릴 수 있습니다. recoveryId=%d, status=%s"
				.formatted(getId(), status));
		}
		this.status = PayoutRecoveryStatus.회수_취소;
	}

	public boolean isRevertibleByRaceCancel() {
		return status == PayoutRecoveryStatus.회수_요청
			|| (status == PayoutRecoveryStatus.회수_완료 && completion == PayoutRecoveryCompletion.상계);
	}

	private void finish(PayoutRecoveryCompletion completion, long completedBy, LocalDateTime completedAt) {
		if (status != PayoutRecoveryStatus.회수_요청) {
			throw new PayoutRecoveryConflictException("이미 완료된 회수입니다.");
		}
		this.status = PayoutRecoveryStatus.회수_완료;
		this.completion = completion;
		this.completedBy = completedBy;
		this.completedAt = completedAt;
	}
}
