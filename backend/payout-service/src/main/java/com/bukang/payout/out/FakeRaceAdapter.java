package com.bukang.payout.out;

import java.time.LocalDate;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 테스트용 어댑터: 상품 서비스를 띄우지 않는 테스트 프로파일에서 대상 대회를 하나도 돌려주지 않는다
 * 배치 테스트는 이 빈 대신 StubRacePort를 직접 넣으므로, 이 어댑터는 스프링 컨텍스트를 띄우기 위해서만 있다
 * dev(로컬)와 prod는 ProductRaceAdapter가 실제 상품 서비스를 부른다 (PRO-224)
 */
@Component
@Profile("test")
public class FakeRaceAdapter implements RacePort {
	@Override
	public List<RaceSchedule> findRacesByRefundDeadline(LocalDate fromRefundDeadline, LocalDate toRefundDeadline) {
		return List.of();
	}

	@Override
	public List<RaceSchedule> findRacesByRaceDate(LocalDate fromRaceDate, LocalDate toRaceDate) {
		return List.of();
	}
}
