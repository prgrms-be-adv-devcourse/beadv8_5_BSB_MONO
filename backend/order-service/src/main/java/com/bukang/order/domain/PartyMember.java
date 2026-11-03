package com.bukang.order.domain;

import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 파티원 (BUK-35). 누가 어느 파티에 언제 합류하고 나갔는지 남긴다. 나가도 줄은 지우지 않는다
 * 한 파티에 같은 사람은 한 줄이라, 나갔다가 다시 합류하면 새 줄 대신 left_at을 비워 되살린다 (11/03)
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "ORDER_PARTY_MEMBER",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_party_member", columnNames = {"party_id", "member_id"})
	}
)
public class PartyMember extends BaseIdAndTime {
	@Column(name = "party_id", nullable = false)
	private long partyId;

	@Column(name = "member_id", nullable = false)
	private long memberId;

	@Column(name = "crew_id", nullable = false)
	private long crewId;

	@Column(name = "joined_at", nullable = false)
	private LocalDateTime joinedAt;

	@Column(name = "left_at")
	private LocalDateTime leftAt;

	private PartyMember(long partyId, long memberId, long crewId, LocalDateTime joinedAt) {
		this.partyId = partyId;
		this.memberId = memberId;
		this.crewId = crewId;
		this.joinedAt = joinedAt;
	}

	public static PartyMember join(long partyId, long memberId, long crewId, LocalDateTime joinedAt) {
		return new PartyMember(partyId, memberId, crewId, joinedAt);
	}

	public boolean isActive() {
		return leftAt == null;
	}

	public void leave(LocalDateTime now) {
		this.leftAt = now;
	}

	// 나갔던 사람이 같은 파티에 다시 합류한다. 줄을 새로 만들지 않고 되살린다 (11/03)
	public void rejoin(LocalDateTime now) {
		this.joinedAt = now;
		this.leftAt = null;
	}
}
