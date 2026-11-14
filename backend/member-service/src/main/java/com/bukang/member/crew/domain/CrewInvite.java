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
 * 초대 링크. 찾기는 토큰의 SHA-256 값으로 한다 (BE-028)
 * 보낸 초대 목록에서 링크를 다시 보여 주려고 원문도 저장한다 (BUK-195). 그 전에 만든 초대는 원문이 없다
 * 상태는 컬럼 하나로 두지 않고 시각으로 판단한다: 취소(canceledAt), 24시간 지남(expiresAt)
 * 링크 하나로 24시간 동안 여러 명이 가입한다 (BUK-135). 누가 어느 초대로 들어왔는지는 크루원의 inviteId에 남는다
 * usedAt·usedBy는 그 전에 한 번 쓰고 닫힌 옛 초대에만 있고, 새로 채우지 않는다
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

	// 토큰 원문 (22자). BUK-195 전에 만든 초대는 null
	@Column(name = "invite_token", length = 22)
	private String inviteToken;

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

	private CrewInvite(long crewId, String inviteToken, String tokenHash, long createdBy, LocalDateTime now) {
		this.crewId = crewId;
		this.inviteToken = inviteToken;
		this.tokenHash = tokenHash;
		this.createdBy = createdBy;
		this.createdAt = now;
		this.expiresAt = now.plus(VALID_DURATION);
		this.updatedAt = now;
	}

	public static CrewInvite create(
		long crewId, String inviteToken, String tokenHash, long createdBy, LocalDateTime now
	) {
		return new CrewInvite(crewId, inviteToken, tokenHash, createdBy, now);
	}
}
