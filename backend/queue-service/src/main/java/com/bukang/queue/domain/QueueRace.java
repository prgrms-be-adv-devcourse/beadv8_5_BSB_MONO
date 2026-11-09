package com.bukang.queue.domain;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

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
 * 줄을 세울 수 있는지 판정하는 칸(판매 상태, 개인 예매 오픈 일시, 접수 마감 일시, 크루 예매 시작·마감 일시)만 복제한다.
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

	// 크루 예매 시작·마감 일시. 둘 다 null이면 크루 모집 없이 개인 예매만 하는 대회다 (파티 대기열이 열리지 않는다)
	private LocalDateTime crewSaleStartAt;

	private LocalDateTime crewSaleEndAt;

	// 원본 대회 행의 수정 일시(race.updated_at). 늦게 온 옛 메시지를 가리는 비교 값이다
	@Column(nullable = false)
	private LocalDateTime updatedAt;

	public QueueRace(
		long id,
		RaceSaleStatus saleStatus,
		LocalDateTime individualOpenAt,
		LocalDateTime applicationCloseAt,
		LocalDateTime crewSaleStartAt,
		LocalDateTime crewSaleEndAt,
		LocalDateTime updatedAt
	) {
		this.id = id;
		this.saleStatus = saleStatus;
		this.individualOpenAt = individualOpenAt;
		this.applicationCloseAt = applicationCloseAt;
		this.crewSaleStartAt = crewSaleStartAt;
		this.crewSaleEndAt = crewSaleEndAt;
		this.updatedAt = updatedAt;
	}

	// 대기열에 들어올 수 있는지: 판매 중이고, 개인 예매 오픈부터 접수 마감 전까지
	// 판매 예정(SCHEDULED)은 아직 판매 전이라 들어올 수 없다 (상품의 자리 확보 판정 Race.isAcceptingIndividual과 다른 점)
	public boolean isEnterable(LocalDateTime now) {
		return saleStatus == RaceSaleStatus.ON_SALE
			&& !now.isBefore(individualOpenAt) && now.isBefore(applicationCloseAt);
	}

	// 파티 대기열에 들어올 수 있는지: 판매 중이고, 크루 예매 시작부터 크루 예매 마감 전까지 (접수 마감 전)
	// 개인 대기열과 같은 기준으로, 크루 예매 시작 일시 정각부터 들어올 수 있다. 크루 모집 없는 대회는 들어올 수 없다
	public boolean isPartyEnterable(LocalDateTime now) {
		if (crewSaleStartAt == null || crewSaleEndAt == null) {
			return false;
		}
		return saleStatus == RaceSaleStatus.ON_SALE
			&& !now.isBefore(crewSaleStartAt) && now.isBefore(crewSaleEndAt) && now.isBefore(applicationCloseAt);
	}

	// 오늘 날짜로 어느 대기열에 들어갈지 정한다. 크루 마지막 날까지는 파티, 그 뒤와 크루 예매가 없는 대회는 개인이다
	// 날짜만 본다. 실제로 들어갈 수 있는지는 대기열 등록이 시각으로 판정한다(isPartyEnterable·isEnterable)
	//   예: 크루 예매 마감 11/12 19:00 → 11/12 21:00에도 파티다. 이때 파티 대기열 등록은 시각 검사에서 막힌다
	public QueueEntryType entryTypeOn(LocalDate today) {
		if (hasCrewSale() && !today.isAfter(crewLastDate())) {
			return QueueEntryType.PARTY;
		}
		return QueueEntryType.INDIVIDUAL;
	}

	// 그 대기열이 열리는 날짜 (파티: 크루 예매 시작 날짜, 개인: 개인 예매 오픈 날짜)
	public LocalDate openDate(QueueEntryType entryType) {
		return switch (entryType) {
			case PARTY -> crewSaleStartAt.toLocalDate();
			case INDIVIDUAL -> individualOpenAt.toLocalDate();
		};
	}

	// 그 대기열의 마지막 날짜 (파티: 크루 마지막 날, 개인: 접수 마감 날짜)
	public LocalDate closeDate(QueueEntryType entryType) {
		return switch (entryType) {
			case PARTY -> crewLastDate();
			case INDIVIDUAL -> applicationCloseAt.toLocalDate();
		};
	}

	// 판매자가 판매를 멈춘 대회인지. 대기열 등록이 기간 문구보다 먼저 판매 중지 문구로 거절할 때 쓴다
	public boolean isStopped() {
		return saleStatus == RaceSaleStatus.STOPPED;
	}

	public boolean isCanceled() {
		return saleStatus == RaceSaleStatus.CANCELED;
	}

	private boolean hasCrewSale() {
		return crewSaleStartAt != null && crewSaleEndAt != null;
	}

	// 크루 예매를 할 수 있는 마지막 날. 마감이 00:00 정각이면 그날은 하루도 열리지 않으므로 전날이다
	//   예: 마감 11/12 19:00 → 11/12, 마감 11/13 00:00 → 11/12 (개인 예매는 11/13 00:00부터 열 수 있다)
	// 상품 서비스 Race.crewCloseAt(크루 예매 마감일의 자정)과 같은 기준이다
	private LocalDate crewLastDate() {
		if (crewSaleEndAt.toLocalTime().equals(LocalTime.MIDNIGHT)) {
			return crewSaleEndAt.toLocalDate().minusDays(1);
		}
		return crewSaleEndAt.toLocalDate();
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
