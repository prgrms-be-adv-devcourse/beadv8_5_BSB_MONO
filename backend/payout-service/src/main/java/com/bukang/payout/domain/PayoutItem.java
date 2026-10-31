package com.bukang.payout.domain;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 원장 줄: 판매자 몫이 얼마 늘었는지(+) 줄었는지(−) 한 번의 움직임 (PRO-214)
 * 정산 내역(payout)에 붙는 줄과 회수(recovery)에 붙는 줄이 있다. 어느 쪽이든 판매자 · 대회를 함께 적어
 * 판매자 × 대회 × 계정으로 잔고를 바로 더할 수 있게 한다
 * 추가만 하고 고치지 않는다. 그래서 setter와 변경 메서드를 두지 않는다
 *
 * 중복 방지 (같은 줄이 두 번 들어가지 않게)
 * - 참가권 줄: (order_item_id, record_type) 유일. 늦게 들어온 참가권이 다른 정산 내역에 또 들어가는 것까지 막는다 (PRO-118)
 * - 참가권 없는 줄(order_item_id NULL): MySQL 유일 인덱스는 NULL끼리 막지 않으므로, NULL일 때만 값을 갖는 칼럼
 *   bundle_key(예: P1:ADVANCE_PAID:C3, R4:RECOVERY_REQUESTED)에 유일 제약을 건다. 코스가 있으면 키에 코스를 넣는다
 *   DB 생성 칼럼으로 두면 테스트 DB(H2)에서 INSERT 때 DB가 닫혀, 저장 직전에 Java에서 계산해 넣는다 (PRO-214, 원인 미확인)
 * - 상계 줄(RECOVERY_OFFSET)은 한 정산 내역에 여러 번 생길 수 있어 bundle_key를 두지 않는다.
 *   상계액을 잔고로 다시 계산하고 정산 내역 행을 잠그므로, 다시 실행해도 0원이라 줄이 생기지 않는다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(name = "PAYOUT_PAYOUT_ITEM",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_payout_item_order_item_record", columnNames = {"order_item_id", "record_type"}),
		@UniqueConstraint(name = "uk_payout_item_bundle_key", columnNames = {"bundle_key"})
	},
	indexes = {
		@Index(name = "idx_payout_item_payout", columnList = "payout_id"),
		@Index(name = "idx_payout_item_seller_race", columnList = "seller_id, race_id, account")
	})
public class PayoutItem extends BaseIdAndTime {
	// 정산 내역에 붙는 줄(1차 정산금 · 판매 · 수수료 · 1차 정산금 차감 · 상계 · 취소)이면 그 정산 내역. 회수 줄이면 NULL
	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "payout_id")
	private Payout payout;

	// 회수 줄(요청 · 입금)이면 그 회수. 정산 내역 줄이면 NULL
	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "recovery_id")
	private PayoutRecovery recovery;

	@Column(nullable = false)
	private long sellerId;

	@Column(nullable = false)
	private long raceId;

	@Enumerated(STRING)
	@Column(nullable = false, columnDefinition = "varchar(20)")
	private PayoutAccount account;

	@Enumerated(STRING)
	@Column(nullable = false, columnDefinition = "varchar(30)")
	private PayoutRecordType recordType;

	// 주문 도메인의 주문 상품 ID. 참가권 줄에만 있고, 참가권 없는 줄(1차 정산금 · 1차 정산금 차감 · 상계 · 대회 취소 회수)은 NULL
	private Long orderItemId;

	// 상품 도메인의 코스 ID. 정산 내역은 대회 단위이고, 판매자가 코스별로 보도록 줄에 코스를 적는다 (PRO-214)
	// 1차 정산금(코스마다 한 줄) · 판매 · 수수료 · 일정 변경 회수 줄에 있고, 1차 정산금 차감 · 상계 · 대회 취소 회수 줄은 NULL
	private Long raceCourseId;

	// 판매자 기준 부호: 판매자에게 가면 +, 판매자에게서 오면 −
	@Column(nullable = false)
	private long amount;

	// 판매 · 수수료 줄의 근거 수수료율 (예: 0.0550). 나중에 수수료율이 바뀌어도 이 줄은 그대로다. 다른 줄은 NULL
	@Column(precision = 5, scale = 4)
	private BigDecimal feeRate;

	// 정산 대상일: 1차는 환불 기한 다음 날, 최종은 대회 다음 날. 회수 줄은 NULL
	private LocalDate targetDate;

	// 참가권 없는 줄의 중복 방지 키. 참가권 줄과 상계 줄은 NULL (저장 직전에 정한다)
	@Column(length = 60, updatable = false)
	private String bundleKey;

	private PayoutItem(Payout payout, PayoutRecovery recovery, long sellerId, long raceId, PayoutRecordType recordType,
		Long orderItemId, Long raceCourseId, long amount, BigDecimal feeRate, LocalDate targetDate) {
		this.payout = payout;
		this.recovery = recovery;
		this.sellerId = sellerId;
		this.raceId = raceId;
		this.account = recordType.getAccount();
		this.recordType = recordType;
		this.orderItemId = orderItemId;
		this.raceCourseId = raceCourseId;
		this.amount = amount;
		this.feeRate = feeRate;
		this.targetDate = targetDate;
	}

	// 정산 내역에 붙는 줄. Payout 안에서만 만든다 (정산 내역 금액 캐시와 함께 바뀌어야 하므로)
	static PayoutItem ofPayout(Payout payout, PayoutRecordType recordType, Long orderItemId, Long raceCourseId,
		long amount, BigDecimal feeRate, LocalDate targetDate) {
		return new PayoutItem(payout, null, payout.getSellerId(), payout.getRaceId(), recordType, orderItemId,
			raceCourseId, amount, feeRate, targetDate);
	}

	// 회수에 붙는 줄 (요청 · 입금)
	public static PayoutItem ofRecovery(PayoutRecovery recovery, PayoutRecordType recordType, Long orderItemId,
		Long raceCourseId, long amount) {
		return new PayoutItem(null, recovery, recovery.getSellerId(), recovery.getRaceId(), recordType, orderItemId,
			raceCourseId, amount, null, null);
	}

	// 청구 입금 확인 줄: 정산 내역 · 회수 어느 쪽에도 붙지 않고 판매자 × 대회로만 남긴다 (선 환불 후 회수 R5)
	// 청구는 판매자 × 대회 합계로 받으므로 회수 한 건에 나눠 적지 않는다. 중복은 청구 ID 키로 막는다
	public static PayoutItem ofClaim(PayoutClaim claim, PayoutRecordType recordType, long amount) {
		PayoutItem item = new PayoutItem(null, null, claim.getSellerId(), claim.getRaceId(), recordType, null, null,
			amount, null, null);
		item.bundleKey = "C%d:%s".formatted(claim.getId(), recordType);
		return item;
	}

	// 정산 내역(payout)은 먼저 저장돼 ID가 있으므로 줄을 저장하기 직전에 키를 만든다
	@PrePersist
	void assignBundleKey() {
		if (orderItemId != null || recordType == PayoutRecordType.RECOVERY_OFFSET
			|| recordType == PayoutRecordType.RECOVERY_OFFSET_CANCELED) {
			return;
		}
		// 1차 정산금은 코스마다 한 줄이라 코스가 있으면 키에 넣는다 (예: P1:ADVANCE_PAID:C3)
		String course = raceCourseId == null ? "" : ":C" + raceCourseId;
		if (payout != null) {
			this.bundleKey = "P%d:%s%s".formatted(payout.getId(), recordType, course);
		} else if (recovery != null) {
			this.bundleKey = "R%d:%s%s".formatted(recovery.getId(), recordType, course);
		}
	}
}
