package com.bukang.order.domain;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 파티 (BUK-35). 같은 크루의 크루원 여럿이 한 대회에 함께 신청하는 묶음이고, 결제는 파티원이 각자 한다
 * 파티는 대회 단위다. 종목은 파티원마다 고르므로 참가 신청(Participation.raceCourseId)에 둔다 (11/02 결정 ①)
 * 파티원 신청서는 Participation.partyId로 이 파티를 가리킨다. 신청·결제 인원은 저장하지 않고 신청서에서 센다 (⑨)
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "ORDER_PARTY",
	uniqueConstraints = {
		// 참가 링크로 파티를 찾는다
		@UniqueConstraint(name = "uk_party_join_token", columnNames = "join_token")
	}
)
public class Party extends BaseIdAndTime {
	// 32바이트 = URL에 쓰는 Base64 43자. 추측할 수 없게 SecureRandom으로 만든다
	private static final int JOIN_TOKEN_BYTES = 32;
	private static final SecureRandom RANDOM = new SecureRandom();

	@Column(name = "crew_id", nullable = false)
	private long crewId;

	@Column(name = "leader_member_id", nullable = false)
	private long leaderMemberId;

	@Column(name = "race_id", nullable = false)
	private long raceId;

	// 참가 링크 토큰. 파티장이 링크를 다시 볼 수 있게 원문으로 둔다(11/03). 로그에 남기지 않는다
	@Column(name = "join_token", nullable = false, length = 64)
	private String joinToken;

	@Enumerated(STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	private PartyStatus status;

	// 파티원 결제 기한 = min(신청 + 24시간, 크루 예매 마감, 접수 마감). 신청 때 정한다
	@Column(name = "payment_due_at")
	private LocalDateTime paymentDueAt;

	@Column(name = "applied_at")
	private LocalDateTime appliedAt;

	@Column(name = "closed_at")
	private LocalDateTime closedAt;

	@Column(name = "canceled_at")
	private LocalDateTime canceledAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	private Party(long crewId, long leaderMemberId, long raceId, String joinToken) {
		this.crewId = crewId;
		this.leaderMemberId = leaderMemberId;
		this.raceId = raceId;
		this.joinToken = joinToken;
		this.status = PartyStatus.RECRUITING;
	}

	// 크루원인지는 호출하는 쪽이 회원 서비스로 확인한다
	public static Party create(long crewId, long leaderMemberId, long raceId) {
		return new Party(crewId, leaderMemberId, raceId, newJoinToken());
	}

	static String newJoinToken() {
		byte[] bytes = new byte[JOIN_TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
