package com.bukang.member.crew.domain;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;
import java.time.YearMonth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.NoArgsConstructor;

/**
 * 크루 파티 만들기 제한 (BUK-187). 한 달에 결제율 미달이 3회인 크루는 저장한 때부터 30일 동안 파티를 만들 수 없다
 * 크루·달마다 한 행만 둔다. start_at <= 지금 < end_at인 행이 있으면 제한 중이다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Table(
	name = "member_crew_penalty",
	uniqueConstraints = @UniqueConstraint(name = "uk_crew_penalty_crew_month",
		columnNames = {"crew_id", "penalty_month"}),
	indexes = @Index(name = "idx_crew_penalty_crew_end", columnList = "crew_id, end_at")
)
public class CrewPenalty {
	public static final int PENALTY_DAYS = 30;

	@Id
	@GeneratedValue(strategy = IDENTITY)
	@Column(name = "crew_penalty_id")
	private long id;

	@Column(name = "crew_id", nullable = false)
	private long crewId;

	// 미달을 센 달 (예: 2026-11)
	@Column(name = "penalty_month", nullable = false, columnDefinition = "char(7)")
	private String penaltyMonth;

	@Column(name = "low_payment_count", nullable = false)
	private int lowPaymentCount;

	@Column(name = "start_at", nullable = false)
	private LocalDateTime startAt;

	@Column(name = "end_at", nullable = false)
	private LocalDateTime endAt;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	private CrewPenalty(long crewId, YearMonth month, int lowPaymentCount, LocalDateTime now) {
		this.crewId = crewId;
		this.penaltyMonth = month.toString();
		this.lowPaymentCount = lowPaymentCount;
		this.startAt = now;
		this.endAt = now.plusDays(PENALTY_DAYS);
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static CrewPenalty start(long crewId, YearMonth month, int lowPaymentCount, LocalDateTime now) {
		return new CrewPenalty(crewId, month, lowPaymentCount, now);
	}
}
