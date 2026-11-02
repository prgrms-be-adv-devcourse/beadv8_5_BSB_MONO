package com.bukang.queue.domain;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import com.bukang.common.global.jpa.entity.BaseEntity;
import com.bukang.common.shared.race.domain.RaceSaleStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대기열 서비스가 대회 변경 메시지(RaceChangedEvent)로 복제해 보관하는 대회
 * ID와 수정 일시는 원본 대회(product-service race) 값을 그대로 쓴다.
 * 줄을 세울 수 있는지 판정하는 칸(판매 상태, 개인 예매 오픈 일시, 접수 마감 일시)만 복제한다.
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(name = "QUEUE_RACE")
public class QueueRace extends BaseEntity {
	@Id
	private long id;

	@Enumerated(STRING)
	@Column(name = "sale_status", nullable = false, columnDefinition = "varchar(20)")
	private RaceSaleStatus saleStatus;

	@Column(nullable = false)
	private LocalDateTime individualOpenAt;

	@Column(nullable = false)
	private LocalDateTime applicationCloseAt;

	// 원본 대회 행의 수정 일시(race.updated_at). 늦게 온 옛 메시지를 가리는 비교 값이다
	@Column(nullable = false)
	private LocalDateTime updatedAt;

	public QueueRace(
		long id,
		RaceSaleStatus saleStatus,
		LocalDateTime individualOpenAt,
		LocalDateTime applicationCloseAt,
		LocalDateTime updatedAt
	) {
		this.id = id;
		this.saleStatus = saleStatus;
		this.individualOpenAt = individualOpenAt;
		this.applicationCloseAt = applicationCloseAt;
		this.updatedAt = updatedAt;
	}

	// 대기열에 들어올 수 있는지: 판매 중이고, 개인 예매 오픈부터 접수 마감 전까지
	// 판매 예정(SCHEDULED)은 아직 판매 전이라 들어올 수 없다 (상품의 자리 확보 판정 Race.isAcceptingIndividual과 다른 점)
	public boolean isEnterable(LocalDateTime now) {
		return saleStatus == RaceSaleStatus.ON_SALE
			&& !now.isBefore(individualOpenAt) && now.isBefore(applicationCloseAt);
	}

	// 대회 메시지에는 생성 일시가 없어 원본 수정 일시를 돌려준다
	@Override
	public LocalDateTime getCreateDate() {
		return updatedAt;
	}

	@Override
	public LocalDateTime getModifyDate() {
		return updatedAt;
	}
}
