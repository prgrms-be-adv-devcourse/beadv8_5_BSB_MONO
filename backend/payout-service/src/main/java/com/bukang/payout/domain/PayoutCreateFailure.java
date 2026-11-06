package com.bukang.payout.domain;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 정산 생성 실패 기록 한 줄 (BUK-60). 배치가 정산 내역을 만들지 못할 때마다 쌓기만 한다
 * 다시 시도는 되돌아보기(7일)가 맡으므로 상태 칸이 없다. 풀렸는지는 그 대회 · 단계의 정산 내역이 있는지로 본다
 * 같은 실패가 매일 반복되면 줄도 매일 하나씩 쌓인다. 날마다 원인이 달라질 수 있어 덮어쓰지 않는다
 * 생성 시각은 BaseIdAndTime의 createDate다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(name = "PAYOUT_PAYOUT_CREATE_FAILURE")
public class PayoutCreateFailure extends BaseIdAndTime {
	private static final DateTimeFormatter NOTE_DATE = DateTimeFormatter.ofPattern("yyyy/M/d");

	@Enumerated(STRING)
	@Column(nullable = false, length = 10)
	private PayoutStage payoutStage;

	// 대회 목록 · 판매 값 조회 실패는 판매자를 모른다
	private Long sellerId;

	// 대회 목록 조회 실패는 대회를 모른다
	private Long raceId;

	// 대사 불일치에서 어긋난 참가권의 코스가 하나일 때만 채운다 (BUK-67). 다른 실패는 NULL
	private Long raceCourseId;

	// 정산 대상일: 최종은 대회일, 1차는 환불 마감일. 대회 목록 조회 실패는 NULL
	private LocalDate payoutTargetDate;

	// DB는 ENUM 칸이다. 종류를 더하면 같은 PR에 ENUM 목록을 바꾸는 Flyway SQL을 넣는다 (PayoutEnumColumnTest가 확인)
	@Enumerated(STRING)
	@Column(nullable = false, length = 20)
	private PayoutCreateFailureType failureType;

	private String note;

	// 배치의 실행 기준일(today). 생성 시각과 달리 dev 배치 API로 다른 날을 돌려도 어느 날의 실행인지 남는다
	@Column(nullable = false)
	private LocalDate runDate;

	private PayoutCreateFailure(PayoutStage payoutStage, Long sellerId, Long raceId, LocalDate payoutTargetDate,
		PayoutCreateFailureType failureType, String note, LocalDate runDate) {
		this(payoutStage, sellerId, raceId, null, payoutTargetDate, failureType, note, runDate);
	}

	private PayoutCreateFailure(PayoutStage payoutStage, Long sellerId, Long raceId, Long raceCourseId,
		LocalDate payoutTargetDate, PayoutCreateFailureType failureType, String note, LocalDate runDate) {
		this.payoutStage = payoutStage;
		this.sellerId = sellerId;
		this.raceId = raceId;
		this.raceCourseId = raceCourseId;
		this.payoutTargetDate = payoutTargetDate;
		this.failureType = failureType;
		this.note = note;
		this.runDate = runDate;
	}

	// 상품 서비스에서 대상 대회 목록을 받지 못했다. 어느 대회가 빠졌는지 모르므로 본 기간만 비고에 남긴다
	public static PayoutCreateFailure raceLookup(PayoutStage stage, LocalDate from, LocalDate to, LocalDate runDate) {
		String note = "from~to: %s~%s".formatted(from.format(NOTE_DATE), to.format(NOTE_DATE));
		return new PayoutCreateFailure(stage, null, null, null, PayoutCreateFailureType.대회_조회_실패, note, runDate);
	}

	// 주문 서비스에서 대회의 판매 값을 받지 못했다. 그 대회의 모든 판매자가 빠진다
	public static PayoutCreateFailure orderLookup(PayoutStage stage, long raceId, LocalDate payoutTargetDate,
		LocalDate runDate) {
		return new PayoutCreateFailure(stage, null, raceId, payoutTargetDate, PayoutCreateFailureType.주문_조회_실패, null,
			runDate);
	}

	// 판매자 × 대회 정산 내역 하나를 만들지 못했다
	public static PayoutCreateFailure sellerRace(PayoutStage stage, long sellerId, long raceId,
		LocalDate payoutTargetDate, PayoutCreateFailureType failureType, String note, LocalDate runDate) {
		return new PayoutCreateFailure(stage, sellerId, raceId, payoutTargetDate, failureType, note, runDate);
	}

	// 정산 대사가 대회 하나를 비교하다 예외로 멈췄다 (PR #330 리뷰). 어느 판매자에서 멈췄는지 모르므로 판매자 칸은 비우고
	// 비고에 예외 이름을 남긴다. 대사가 조용히 빠지지 않게 한다
	public static PayoutCreateFailure reconcileError(PayoutStage stage, long raceId, LocalDate payoutTargetDate,
		String exceptionName, LocalDate runDate) {
		return new PayoutCreateFailure(stage, null, raceId, payoutTargetDate, PayoutCreateFailureType.알_수_없는_실패,
			exceptionName, runDate);
	}

	// 정산 대사가 판매자 × 대회의 어긋남을 찾았다 (BUK-67). 비고에는 장수 · 금액만 쓰고 참가권 ID는 로그에 남긴다
	public static PayoutCreateFailure reconcileMismatch(PayoutStage stage, long sellerId, long raceId,
		Long raceCourseId, LocalDate payoutTargetDate, String note, LocalDate runDate) {
		return new PayoutCreateFailure(stage, sellerId, raceId, raceCourseId, payoutTargetDate,
			PayoutCreateFailureType.대사_불일치, note, runDate);
	}
}
