package com.bukang.member.crew.domain;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

/**
 * 크루에서 내보낸 기록 (BUK-135). 내보내기 전에 만든 초대 링크로는 그 회원이 다시 가입할 수 없다
 * 크루장이 내보낸 뒤 새로 만든 링크로는 다시 가입할 수 있다. 스스로 탈퇴한 경우는 남기지 않는다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Table(
	name = "member_crew_removal",
	indexes = @Index(name = "idx_crew_removal_crew_member", columnList = "crew_id, member_id, removed_at")
)
public class CrewRemoval {
	@Id
	@GeneratedValue(strategy = IDENTITY)
	@Column(name = "removal_id")
	private long id;

	@Column(name = "crew_id", nullable = false)
	private long crewId;

	@Column(name = "member_id", nullable = false)
	private long memberId;

	@Column(name = "removed_at", nullable = false)
	private LocalDateTime removedAt;

	private CrewRemoval(long crewId, long memberId, LocalDateTime removedAt) {
		this.crewId = crewId;
		this.memberId = memberId;
		this.removedAt = removedAt;
	}

	public static CrewRemoval of(long crewId, long memberId, LocalDateTime removedAt) {
		return new CrewRemoval(crewId, memberId, removedAt);
	}
}
