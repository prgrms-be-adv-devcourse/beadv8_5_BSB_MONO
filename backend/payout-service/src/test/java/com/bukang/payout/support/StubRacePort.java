package com.bukang.payout.support;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import com.bukang.payout.out.RacePort;
import com.bukang.payout.out.RaceSchedule;
import com.bukang.payout.out.SoldEntry;

/**
 * 테스트용 상품 서비스: 대회 일정(환불 마감일 · 대회일)과 이름을 케이스마다 정해 돌려준다
 * 1차 대상은 넘겨받은 일정으로만 고른다. 최종 대상은 넘겨받은 일정에 더해, 판매 내역에 나온 대회를
 * 이름이 있으면(상품 서비스가 아는 대회면) 그 참가권의 대회일로 만들어 함께 돌려준다
 * 실제 앱의 FakeRaceAdapter는 대상 대회를 하나도 돌려주지 않는다
 */
public class StubRacePort implements RacePort {
	private final List<RaceSchedule> schedules;

	public StubRacePort(IntFunction<Optional<String>> raceNames, List<RaceSchedule> schedules,
		List<SoldEntry> soldEntries) {
		Set<Integer> known = schedules.stream().map(RaceSchedule::raceId).collect(Collectors.toSet());
		List<RaceSchedule> all = new ArrayList<>(schedules);
		soldEntries.stream()
			.filter(entry -> known.add(entry.raceId()))
			.forEach(entry -> raceNames.apply(entry.raceId()).ifPresent(name ->
				// 최종 대상으로만 쓰도록 환불 마감일은 1차 기간에 걸리지 않게 멀리 둔다
				all.add(new RaceSchedule(entry.raceId(), name, entry.raceDate(), entry.raceDate().minusYears(1)))));
		this.schedules = List.copyOf(all);
	}

	@Override
	public List<RaceSchedule> findRacesByRefundDeadline(LocalDate fromRefundDeadline, LocalDate toRefundDeadline) {
		return schedules.stream()
			.filter(race -> !race.refundDeadline().isBefore(fromRefundDeadline)
				&& !race.refundDeadline().isAfter(toRefundDeadline))
			.toList();
	}

	@Override
	public List<RaceSchedule> findRacesByRaceDate(LocalDate fromRaceDate, LocalDate toRaceDate) {
		return schedules.stream()
			.filter(race -> !race.raceDate().isBefore(fromRaceDate) && !race.raceDate().isAfter(toRaceDate))
			.toList();
	}
}
