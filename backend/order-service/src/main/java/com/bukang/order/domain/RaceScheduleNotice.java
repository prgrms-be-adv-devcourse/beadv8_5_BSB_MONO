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
 * 결제자에게 「대회 일정·장소가 바뀌었어요」를 마지막으로 알린 일정 변경 일시 (RaceScheduleNoticeUseCase, BUK-82)
 * 대회마다 한 줄이다(대회 ID 유니크). 같은 일정 변경으로는 다시 알리지 않고, 더 새로운 변경이 오면 값을 바꾸고 다시 알린다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "ORDER_RACE_SCHEDULE_NOTICE",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_race_schedule_notice_race", columnNames = {"race_id"})
	}
)
public class RaceScheduleNotice extends BaseIdAndTime {
	@Column(name = "race_id", nullable = false)
	private long raceId;

	// 상품 race.schedule_changed_at 사본. 이 값으로 알렸다
	@Column(name = "schedule_changed_at", nullable = false)
	private LocalDateTime scheduleChangedAt;

	public static RaceScheduleNotice of(long raceId, LocalDateTime scheduleChangedAt) {
		RaceScheduleNotice notice = new RaceScheduleNotice();
		notice.raceId = raceId;
		notice.scheduleChangedAt = scheduleChangedAt;
		return notice;
	}

	// 이 일정 변경을 이미 알렸는지. 늦게 온 옛 메시지(더 이른 변경 일시)도 알린 것으로 본다
	public boolean alreadyNotified(LocalDateTime changedAt) {
		return !changedAt.isAfter(scheduleChangedAt);
	}

	public void markNotified(LocalDateTime changedAt) {
		this.scheduleChangedAt = changedAt;
	}
}
