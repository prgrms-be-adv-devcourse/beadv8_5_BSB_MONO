package com.bukang.order.domain;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;
import com.bukang.order.domain.exception.PartyAccessDeniedException;
import com.bukang.order.domain.exception.PartyConflictException;

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
 * 파티원은 모두 파티장이 만들 때 고른 종목으로 신청하고, 만든 뒤에는 종목을 바꿀 수 없다 (11/05 결정, 11/02 결정 ①을 바꿈)
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
	// 참가 링크는 만든 뒤 24시간만 쓸 수 있다 (정책 3장 「파티 만들기와 합류」)
	static final Duration JOIN_LINK_VALIDITY = Duration.ofHours(24);

	@Column(name = "crew_id", nullable = false)
	private long crewId;

	@Column(name = "leader_member_id", nullable = false)
	private long leaderMemberId;

	@Column(name = "race_id", nullable = false)
	private long raceId;

	// 만든 뒤 바꾸지 않는다. 다른 종목으로 가려면 파티를 새로 만든다
	@Column(name = "race_course_id", nullable = false)
	private long raceCourseId;

	// 참가 링크 토큰. 파티장이 링크를 다시 볼 수 있게 원문으로 둔다(11/03). 로그에 남기지 않는다
	@Column(name = "join_token", nullable = false, length = 64)
	private String joinToken;

	@Column(name = "join_token_issued_at", nullable = false)
	private LocalDateTime joinTokenIssuedAt;

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

	private Party(long crewId, long leaderMemberId, long raceId, long raceCourseId, LocalDateTime now) {
		this.crewId = crewId;
		this.leaderMemberId = leaderMemberId;
		this.raceId = raceId;
		this.raceCourseId = raceCourseId;
		this.status = PartyStatus.RECRUITING;
		issueJoinToken(now);
	}

	// 크루원인지는 호출하는 쪽이 회원 서비스로 확인한다
	public static Party create(long crewId, long leaderMemberId, long raceId, long raceCourseId,
		LocalDateTime now) {
		return new Party(crewId, leaderMemberId, raceId, raceCourseId, now);
	}

	public LocalDateTime getJoinTokenExpiresAt() {
		return joinTokenIssuedAt.plus(JOIN_LINK_VALIDITY);
	}

	public boolean isLeader(long memberId) {
		return leaderMemberId == memberId;
	}

	// 합류·나가기·링크 다시 만들기는 신청 전(모집 중)에만 된다. 결제 진행 중에는 파티원을 바꿀 수 없다 (정책 6장)
	public void checkRecruiting() {
		if (status != PartyStatus.RECRUITING) {
			throw PartyConflictException.notRecruiting(getId(), status);
		}
	}

	// 다시 만들면 옛 링크는 더 이상 찾을 수 없다 (토큰이 바뀜)
	public void regenerateJoinToken(long memberId, LocalDateTime now) {
		checkLeader(memberId);
		checkRecruiting();
		issueJoinToken(now);
	}

	public void checkJoinLinkAlive(LocalDateTime now) {
		if (!now.isBefore(getJoinTokenExpiresAt())) {
			throw PartyConflictException.linkExpired();
		}
	}

	// 신청 전에 파티장이 떠나면 가장 먼저 합류한 파티원에게 넘긴다 (11/02 결정 ⑪)
	public void handOverLeader(long newLeaderMemberId) {
		this.leaderMemberId = newLeaderMemberId;
	}

	// 남은 파티원이 없어 파티를 끝낸다
	public void close(LocalDateTime now) {
		this.status = PartyStatus.CLOSED;
		this.closedAt = now;
	}

	private void checkLeader(long memberId) {
		if (!isLeader(memberId)) {
			throw PartyAccessDeniedException.leaderOnly();
		}
	}

	private void issueJoinToken(LocalDateTime now) {
		this.joinToken = newJoinToken();
		this.joinTokenIssuedAt = now;
	}

	static String newJoinToken() {
		byte[] bytes = new byte[JOIN_TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
