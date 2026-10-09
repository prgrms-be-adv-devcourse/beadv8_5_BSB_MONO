package com.bukang.payout.domain;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PACKAGE;
import static lombok.AccessLevel.PROTECTED;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 정산 항목: 정산 내역에 묶인 참가권 1장 (주문 상품 하나)
 * 추가만 하고 고치지 않는다. 그래서 setter와 변경 메서드를 두지 않는다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor(access = PACKAGE)
@Getter
@Table(name = "PAYOUT_PAYOUT_ITEM")
public class PayoutItem extends BaseIdAndTime {
	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "payout_id", nullable = false)
	private Payout payout;

	// 주문 도메인의 주문 상품 ID
	@Column(nullable = false)
	private int orderItemId;

	@Enumerated(STRING)
	@Column(nullable = false, length = 20)
	private PayoutRecordType recordType;

	// 판매는 +, 환불은 -
	@Column(nullable = false)
	private long amount;

	// 기록 시점의 수수료율 (예: 0.0550). 나중에 수수료율이 바뀌어도 이 항목은 그대로다
	@Column(nullable = false, precision = 5, scale = 4)
	private BigDecimal feeRate;

	// 정산 대상일: 대회 다음 날
	private LocalDate targetDate;
}
