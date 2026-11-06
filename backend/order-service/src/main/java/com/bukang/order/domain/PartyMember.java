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

	// 크루에 가입한 시각 (회원 서비스 값). 크루 탈퇴 이벤트의 joinedAt이 이 값과 같거나 늦을 때만 파티에서 뺀다
	// 늦게 도착한 탈퇴 이벤트가 다시 가입한 사람을 빼지 않게 한다 (CrewMemberLeftEvent, BUK-77)
	@Column(name = "crew_joined_at", nullable = false)
	private LocalDateTime crewJoinedAt;

	@Column(name = "left_at")
	private LocalDateTime leftAt;

	private PartyMember(long partyId, long memberId, long crewId, LocalDateTime crewJoinedAt, LocalDateTime joinedAt) {
		this.partyId = partyId;
		this.memberId = memberId;
		this.crewId = crewId;
		this.crewJoinedAt = crewJoinedAt;
		this.joinedAt = joinedAt;
	}

	public static PartyMember join(long partyId, long memberId, long crewId, LocalDateTime crewJoinedAt,
		LocalDateTime joinedAt) {
		return new PartyMember(partyId, memberId, crewId, crewJoinedAt, joinedAt);
	}

	public boolean isActive() {
		return leftAt == null;
	}

	public void leave(LocalDateTime now) {
		this.leftAt = now;
	}

	// 나갔던 사람이 같은 파티에 다시 합류한다. 줄을 새로 만들지 않고 되살린다 (11/03)
	// 그 사이 크루를 나갔다 다시 가입했을 수 있으므로 크루 가입 시각도 새로 받는다
	public void rejoin(LocalDateTime crewJoinedAt, LocalDateTime now) {
		this.crewJoinedAt = crewJoinedAt;
		this.joinedAt = now;
		this.leftAt = null;
	}
}
