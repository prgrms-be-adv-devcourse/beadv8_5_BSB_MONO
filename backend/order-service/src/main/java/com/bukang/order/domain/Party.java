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
import jakarta.persistence.Index;
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
	},
	indexes = {
		// 판매자 파티별 내역이 대회로 찾고 최근 파티부터 읽는다 (BUK-227)
		@Index(name = "idx_party_race", columnList = "race_id, id")
	}
)
public class Party extends BaseIdAndTime {
	// 32바이트 = URL에 쓰는 Base64 43자. 추측할 수 없게 SecureRandom으로 만든다
	private static final int JOIN_TOKEN_BYTES = 32;
	private static final SecureRandom RANDOM = new SecureRandom();
	// 참가 링크는 만든 뒤 24시간만 쓸 수 있다 (정책 3장 「파티 만들기와 합류」)
	static final Duration JOIN_LINK_VALIDITY = Duration.ofHours(24);
	// 추천값(미확정, BUK-38): 결제 기한 3시간 전에 결제하지 않은 파티원에게 마감 알림을 한 번 보낸다
	public static final Duration PAYMENT_REMIND_BEFORE = Duration.ofHours(3);
	// 상품을 부르는 중으로 보는 가장 긴 시간. 상품 호출 제한(order.clients.timeout 2초)보다 넉넉히 둔다
	// 추천값(미확정): 부르다 서버가 죽어 끝 시각이 남지 않아도 이만큼 지나면 파티장이 다시 신청할 수 있다
	public static final Duration CREW_HOLD_CALL_LIMIT = Duration.ofMinutes(1);

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

	// 파티원 결제 기한 = 상품이 잡은 크루 자리 기한 (min(신청 + 24시간, 크루 자리 닫는 시각, 접수 마감)). 신청 때 정한다
	@Column(name = "payment_due_at")
	private LocalDateTime paymentDueAt;

	// 결제 마감 알림을 보낸 시각. 한 번만 보낸다 (BUK-38)
	@Column(name = "payment_reminded_at")
	private LocalDateTime paymentRemindedAt;

	// 상품에 크루 자리를 마지막으로 요청한 시각. 결과를 모르는 동안(응답 시간 초과 등) 합류·나가기를 막는다 (BUK-105)
	// 결제 기간을 열거나, 상품이 확정해서 답하면(모자람·거절·지난 기한) 비운다
	@Column(name = "crew_hold_requested_at")
	private LocalDateTime crewHoldRequestedAt;

	// 그 요청이 결과를 모른 채 끝난 시각(503 등). 비어 있으면 요청이 아직 상품을 부르는 중이다 → 같은 파티 신청을 또 받지 않는다
	@Column(name = "crew_hold_call_ended_at")
	private LocalDateTime crewHoldCallEndedAt;

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

	// 합류·나가기: 모집 중이고, 상품에 크루 자리를 요청해 결과를 모르는 중이 아니어야 한다
	// 상품은 같은 파티의 자리 목록이 처음과 다르면 거절하므로, 그사이 파티원이 바뀌면 다시 신청할 수 없게 된다
	public void checkMembersChangeable() {
		checkRecruiting();
		if (crewHoldRequestedAt != null) {
			throw PartyConflictException.crewHoldInProgress(getId());
		}
	}

	// 상품을 부르기 전에 남겨 커밋한다. 다른 요청이 상품을 부르는 중이면 막는다 (두 요청이 엇갈려 결과를 덮지 않게)
	// 503으로 끝난 뒤의 재시도는 부르는 중이 아니므로 받는다. 부르다 서버가 죽어 끝 시각이 없으면 CREW_HOLD_CALL_LIMIT 뒤에 받는다
	public void markCrewHoldRequested(LocalDateTime now) {
		if (crewHoldRequestedAt != null && crewHoldCallEndedAt == null
			&& now.isBefore(crewHoldRequestedAt.plus(CREW_HOLD_CALL_LIMIT))) {
			throw PartyConflictException.applyInProgress(getId());
		}
		this.crewHoldRequestedAt = now;
		this.crewHoldCallEndedAt = null;
	}

	// 상품 응답을 모른 채 요청이 끝났다(503 등). 표시는 남겨 파티원을 못 바꾸게 하고, 파티장의 재시도는 받는다
	public void markCrewHoldCallEnded(LocalDateTime now) {
		if (crewHoldRequestedAt != null) {
			this.crewHoldCallEndedAt = now;
		}
	}

	// 상품이 확정해서 답했다 (모자람, 거절, 지난 기한). 새로 잡힌 자리가 없으니 파티원을 다시 바꿀 수 있다
	public void clearCrewHoldRequested() {
		this.crewHoldRequestedAt = null;
		this.crewHoldCallEndedAt = null;
	}

	// 파티장 [신청] 전 확인 (BUK-37). 이미 신청한 파티는 모집 중이 아니라 409다 (11/02 결정 ⑩: 같은 파티 재신청 막음)
	public void checkApplicable(long memberId) {
		checkLeader(memberId);
		if (status != PartyStatus.RECRUITING) {
			throw PartyConflictException.alreadyApplied(getId(), status);
		}
	}

	// 크루 자리를 모두 잡아 파티원 결제 기간을 연다
	public void startPayment(LocalDateTime paymentDueAt, LocalDateTime now) {
		checkRecruiting();
		this.status = PartyStatus.PAYING;
		this.crewHoldRequestedAt = null;
		this.crewHoldCallEndedAt = null;
		this.paymentDueAt = paymentDueAt;
		this.appliedAt = now;
		// 신청 때 결제 알림을 막 보냈는데 기한이 3시간 안이면 마감 알림을 또 보내지 않는다
		if (!now.isBefore(paymentDueAt.minus(PAYMENT_REMIND_BEFORE))) {
			this.paymentRemindedAt = now;
		}
	}

	// 마감 알림을 보낼 때인지: 결제 진행 중, 아직 안 보냄, 기한 3시간 전 ~ 기한 전
	public boolean needsPaymentReminder(LocalDateTime now) {
		return status == PartyStatus.PAYING && paymentRemindedAt == null
			&& !now.isBefore(paymentDueAt.minus(PAYMENT_REMIND_BEFORE)) && now.isBefore(paymentDueAt);
	}

	public void markPaymentReminded(LocalDateTime now) {
		this.paymentRemindedAt = now;
	}

	// 결제 기간을 끝낸다: 모두 결제했거나 기한이 지났다 (BUK-38). 결제한 사람이 있으면 결제 끝, 없으면 참가할 사람이 없어 종료
	public void finishPayment(boolean anyPaid, LocalDateTime now) {
		if (status != PartyStatus.PAYING) {
			throw new IllegalStateException("결제 진행 중인 파티만 결제를 끝낼 수 있습니다. partyId=%d, status=%s"
				.formatted(getId(), status));
		}
		if (anyPaid) {
			this.status = PartyStatus.PAID;
		} else {
			close(now);
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
