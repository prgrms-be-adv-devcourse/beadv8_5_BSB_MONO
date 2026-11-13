package com.bukang.payout.domain;

import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

/**
 * ShedLock이 쓰는 잠금 표 (BUK-167). 코드에서는 읽고 쓰지 않고 ShedLock(JdbcTemplateLockProvider)만 쓴다
 * 엔티티로 두는 이유: 테스트(H2)는 Flyway 대신 Hibernate가 표를 만들고, dev · prod의 validate가 Flyway 표와 칸을 맞춰 본다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Table(name = "PAYOUT_SHEDLOCK")
public class PayoutSchedulerLock {
	@Id
	@Column(length = 64)
	private String name;

	@Column(nullable = false)
	private LocalDateTime lockUntil;

	@Column(nullable = false)
	private LocalDateTime lockedAt;

	@Column(nullable = false)
	private String lockedBy;
}
