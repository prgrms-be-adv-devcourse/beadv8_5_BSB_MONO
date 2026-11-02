package com.bukang.member.crew.domain;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import java.time.Duration;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 초대 링크. 토큰 원문은 저장하지 않고 SHA-256 값만 저장한다 (BE-028)
 * 상태는 컬럼 하나로 두지 않고 시각으로 판단한다: 쓰임(usedAt), 취소(canceledAt), 24시간 지남(expiresAt)
 * 그래서 만료를 바꿔 주는 배치가 필요 없다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "member_crew_invite",
	uniqueConstraints = @UniqueConstraint(name = "uk_crew_invite_token", columnNames = "token_hash"),
	indexes = @Index(name = "idx_crew_invite_crew", columnList = "crew_id, expires_at")
)
public class CrewInvite {
	public static final Duration VALID_DURATION = Duration.ofHours(24);

	@Id
	@GeneratedValue(strategy = IDENTITY)
	@Column(name = "invite_id")
	private long id;

	@Column(name = "crew_id", nullable = false)
	private long crewId;

	// 토큰의 SHA-256 값 (16진수 64자)
	@Column(name = "token_hash", nullable = false, length = 64, columnDefinition = "char(64)")
	private String tokenHash;

	// 보낸 회원 ID
	@Column(name = "created_by", nullable = false)
	private long createdBy;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "used_at")
	private LocalDateTime usedAt;

	@Column(name = "used_by")
	private Long usedBy;

	@Column(name = "canceled_at")
	private LocalDateTime canceledAt;

	@Column(name = "canceled_by")
	private Long canceledBy;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	private CrewInvite(long crewId, String tokenHash, long createdBy, LocalDateTime now) {
		this.crewId = crewId;
		this.tokenHash = tokenHash;
		this.createdBy = createdBy;
		this.createdAt = now;
		this.expiresAt = now.plus(VALID_DURATION);
		this.updatedAt = now;
	}

	public static CrewInvite create(long crewId, String tokenHash, long createdBy, LocalDateTime now) {
		return new CrewInvite(crewId, tokenHash, createdBy, now);
	}
}
