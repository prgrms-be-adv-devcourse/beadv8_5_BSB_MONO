package com.bukang.product.domain;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 임시 확보한 자리 1석 (ERD v5 participation_hold). 참가 신청 1건 = 행 1개다.
 * 흐름: 대기열을 통과하면 활성 시간(10분)을 받고, [결제하기]를 누르면 주문 서비스가 자리를 확보한다.
 * 자리는 활성 시간이 끝날 때까지만 유지하고, 끝나면 주문 서비스가 해제 메시지(product.hold.cancel)를 보낸다.
 * 상태는 모두 조건부 UPDATE(WHERE status = 'HELD' …)로 바꾸고, 바뀐 행이 1개일 때만 같은 트랜잭션에서 종목 카운터를 바꾼다.
 * 읽고 확인한 뒤 바꾸면, 같은 자리를 판매와 해제가 함께 처리해 카운터가 두 번 바뀔 수 있다.
 * 결제 기한 연장(ERD extended_at)은 하지 않기로 해(10/13) 칸을 두지 않는다.
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "participation_hold",
	indexes = @Index(name = "idx_participation_hold_status_expires", columnList = "status, expires_at")
)
@AttributeOverride(name = "id", column = @Column(name = "participation_hold_id"))
// 확보 일시(held_at)가 행을 만든 일시다
@AttributeOverride(name = "createDate", column = @Column(name = "held_at", nullable = false, updatable = false))
@AttributeOverride(name = "modifyDate", column = @Column(name = "updated_at", nullable = false))
public class ParticipationHold extends BaseIdAndTime {
	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "race_course_id", nullable = false)
	private RaceCourse raceCourse;

	// 주문 서비스의 참가 신청 ID (ERD 이름은 application_id, 주문 서비스는 참가 신청을 participation으로 부른다)
	// 같은 신청으로 두 번 확보해도 자리는 한 번만 잡히게 하는 멱등 키다 (UK)
	@Column(name = "application_id", nullable = false, unique = true)
	private long participationId;

	// 크루 참가 신청 ID. 크루 자리만 있다 (null = 개인 자리). 세미는 개인 예매만 해서 늘 null이다
	@Column(name = "crew_application_id")
	private Long crewApplicationId;

	@Enumerated(STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	private HoldStatus status;

	// 결제 기한 = 활성 시간이 끝나는 시각. 주문 서비스가 확보할 때 보낸다
	// 해제 메시지가 늦게 와도, 이 시각이 지난 뒤의 판매 요청은 거절한다
	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	private LocalDateTime soldAt;

	private LocalDateTime releasedAt;

	@Enumerated(STRING)
	@Column(name = "release_reason", columnDefinition = "varchar(20)")
	private HoldReleaseReason releaseReason;

	private ParticipationHold(RaceCourse raceCourse, long participationId, LocalDateTime expiresAt) {
		this.raceCourse = raceCourse;
		this.participationId = participationId;
		this.expiresAt = expiresAt;
		this.status = HoldStatus.HELD;
	}

	// 개인 자리 1석. 종목 카운터를 올린 뒤에만 만든다
	public static ParticipationHold individual(RaceCourse raceCourse, long participationId, LocalDateTime expiresAt) {
		return new ParticipationHold(raceCourse, participationId, expiresAt);
	}
}
