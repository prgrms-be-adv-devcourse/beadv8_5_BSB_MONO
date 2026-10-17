package com.bukang.payout.out;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 임시 어댑터: 상품 서비스(B)와 아직 연결하지 않아 대상 대회를 하나도 돌려주지 않는다
 * 가짜 날짜로 정산(실제 돈)을 만들지 않기 위해서다. 그래서 이 어댑터를 쓰는 동안 1차 · 최종 정산 모두 만들어지지 않는다
 * 상품 서비스와 연결할 때(HTTP 호출) 이 클래스를 진짜 어댑터로 바꾼다 (주문 서비스의 FakeRaceCourseAdapter와 같은 방식)
 */
@Slf4j
@Component
public class FakeRaceAdapter implements RacePort {
	// 호출마다 경고를 남기면 로그가 묻히므로, 앱을 켤 때 한 번만 알린다
	public FakeRaceAdapter() {
		log.warn("상품 서비스 연결 전이라 정산 대상 대회를 돌려주지 않습니다(FakeRaceAdapter). 정산이 만들어지지 않습니다.");
	}

	@Override
	public List<RaceSchedule> findRacesByRefundDeadline(LocalDate fromRefundDeadline, LocalDate toRefundDeadline) {
		return List.of();
	}

	@Override
	public List<RaceSchedule> findRacesByRaceDate(LocalDate fromRaceDate, LocalDate toRaceDate) {
		return List.of();
	}
}
