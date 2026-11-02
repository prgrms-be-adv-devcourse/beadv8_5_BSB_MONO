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
 * 신청서에 들어올 때 대기열에서 받은 입장 끝 시각 (10/17 회의 2번, PRO-249)
 * 대기열에는 신청서 진입 때(입장 상태 API) 한 번만 묻고, [결제하기]·장바구니 결제는 대기열 대신 이 값을 읽어 결제 기한으로 쓴다
 * 그래서 신청서를 쓰는 동안 대기열이 멈춰도 끝 시각 안이면 결제할 수 있다. 회원 × 종목마다 한 줄이고, 다시 입장하면 덮어쓴다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "ORDER_QUEUE_ADMISSION",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_queue_admission_member_course", columnNames = {"member_id", "race_course_id"})
	}
)
public class QueueAdmission extends BaseIdAndTime {
	@Column(name = "member_id", nullable = false)
	private long memberId;

	@Column(name = "race_course_id", nullable = false)
	private long raceCourseId;

	@Column(name = "admission_ends_at", nullable = false)
	private LocalDateTime admissionEndsAt;

	private QueueAdmission(long memberId, long raceCourseId, LocalDateTime admissionEndsAt) {
		this.memberId = memberId;
		this.raceCourseId = raceCourseId;
		this.admissionEndsAt = admissionEndsAt;
	}

	public static QueueAdmission of(long memberId, long raceCourseId, LocalDateTime admissionEndsAt) {
		return new QueueAdmission(memberId, raceCourseId, admissionEndsAt);
	}

	// 다시 입장했으면 대기열이 준 새 끝 시각으로 바꾼다
	public void admitUntil(LocalDateTime endsAt) {
		this.admissionEndsAt = endsAt;
	}

	public boolean isAdmittedAt(LocalDateTime now) {
		return admissionEndsAt.isAfter(now);
	}
}
