package com.bukang.queue.domain;

import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import com.bukang.common.global.jpa.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대기열 서비스가 종목 변경 메시지(RaceCourseChangedEvent)로 복제해 보관하는 종목
 * ID와 수정 일시는 원본 종목(product-service race_course) 값을 그대로 쓴다.
 * 진입 판정에 쓰는 값은 모두 대회에 있어, 종목은 "이 대회에 있는 종목인가"만 알 수 있게 대회 ID와 삭제 표시만 복제한다.
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(name = "QUEUE_RACE_COURSE")
public class QueueRaceCourse extends BaseEntity {
	@Id
	private long id;

	// 종목이 속한 대회 ID. 종목 메시지가 대회 메시지보다 먼저 올 수 있어 대회 복제본(QUEUE_RACE)에 외래 키를 걸지 않는다
	@Column(nullable = false)
	private long raceId;

	// 원본 종목 행의 수정 일시(race_course.updated_at). 뺀 종목이면 삭제 시각이다. 늦게 온 옛 메시지를 가리는 비교 값이다
	@Column(nullable = false)
	private LocalDateTime updatedAt;

	// 대회에서 뺀 종목이면 true. 행을 지우지 않고 남겨, 늦게 온 옛 메시지가 뺀 종목을 되살리지 못하게 한다
	@Column(nullable = false)
	private boolean deleted;

	public QueueRaceCourse(long id, long raceId, LocalDateTime updatedAt, boolean deleted) {
		this.id = id;
		this.raceId = raceId;
		this.updatedAt = updatedAt;
		this.deleted = deleted;
	}

	// 이 대회에 묶인 종목인지. 대회에서 뺀 종목(삭제 표시)은 묶이지 않은 것으로 본다
	public boolean belongsTo(long raceId) {
		return !deleted && this.raceId == raceId;
	}

	// 종목 메시지에는 생성 일시가 없어 원본 수정 일시를 돌려준다
	@Override
	public LocalDateTime getCreateDate() {
		return updatedAt;
	}

	@Override
	public LocalDateTime getModifyDate() {
		return updatedAt;
	}
}
