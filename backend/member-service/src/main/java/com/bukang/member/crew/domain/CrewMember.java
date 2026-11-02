package com.bukang.member.crew.domain;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 크루원. 지금 크루에 있는 사람만 행이 있고, 탈퇴·내보내기는 행을 지운다 (BE-029)
 * 같은 회원이 같은 크루에 두 번 들어올 수 없다 (uk_crew_member)
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "member_crew_member",
	uniqueConstraints = @UniqueConstraint(name = "uk_crew_member", columnNames = {"crew_id", "member_id"}),
	indexes = @Index(name = "idx_crew_member_member", columnList = "member_id")
)
public class CrewMember {
	@Id
	@GeneratedValue(strategy = IDENTITY)
	@Column(name = "crew_member_id")
	private long id;

	@Column(name = "crew_id", nullable = false)
	private long crewId;

	// 회원 ID. 외래 키를 걸지 않는다
	@Column(name = "member_id", nullable = false)
	private long memberId;

	@Enumerated(STRING)
	@Column(name = "role", nullable = false, columnDefinition = "varchar(20)")
	private CrewRole role;

	@Column(name = "joined_at", nullable = false)
	private LocalDateTime joinedAt;

	// 가입에 쓴 초대. 크루를 만든 크루장은 null
	@Column(name = "invite_id")
	private Long inviteId;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	private CrewMember(long crewId, long memberId, CrewRole role, Long inviteId, LocalDateTime now) {
		this.crewId = crewId;
		this.memberId = memberId;
		this.role = role;
		this.joinedAt = now;
		this.inviteId = inviteId;
		this.createdAt = now;
		this.updatedAt = now;
	}

	// 크루를 만든 회원이 크루장이 된다. 가입 시각은 크루를 만든 시각과 같다
	public static CrewMember leader(long crewId, long memberId, LocalDateTime now) {
		return new CrewMember(crewId, memberId, CrewRole.LEADER, null, now);
	}

	// 초대 링크로 가입하면 크루원 역할이다
	public static CrewMember invited(long crewId, long memberId, long inviteId, LocalDateTime now) {
		return new CrewMember(crewId, memberId, CrewRole.MEMBER, inviteId, now);
	}
}
