package com.bukang.payout.support;

import java.time.LocalDate;

import com.bukang.payout.out.RaceSchedule;
import com.bukang.payout.out.SoldEntry;

/**
 * 정산 테스트의 준비 데이터를 실제와 비슷한 기본값으로 만든다 (카카오페이 기술 블로그 "Given 지옥에서 벗어나기"의 DomainFixture)
 * 테스트는 관심 있는 값(날짜 등)만 넘기고, 판매자 · 대회 · 가격 같은 나머지는 여기의 기본값을 쓴다
 * 자바에는 기본 인자가 없으므로, 자주 바꾸는 값만 인자로 받는 메서드를 둔다. 필요한 만큼만 조금씩 늘린다
 */
public final class PayoutFixture {
	public static final long SELLER_ID = 1;
	public static final long RACE_ID = 10;
	public static final long RACE_COURSE_ID = 10;
	public static final String RACE_NAME = "2026 한강 나이트런";
	public static final long ORDER_ITEM_ID = 100;
	public static final long PRICE = 50_000L;

	private PayoutFixture() {
	}

	// 기본 판매자 · 대회 · 코스의 참가권 한 장 (5만 원)
	public static SoldEntry soldEntry(LocalDate raceDate) {
		return new SoldEntry(ORDER_ITEM_ID, SELLER_ID, RACE_ID, RACE_COURSE_ID, raceDate, PRICE);
	}

	// 기본 대회의 일정
	public static RaceSchedule raceSchedule(LocalDate raceDate, LocalDate refundDeadline) {
		return new RaceSchedule(RACE_ID, RACE_NAME, raceDate, refundDeadline);
	}
}
