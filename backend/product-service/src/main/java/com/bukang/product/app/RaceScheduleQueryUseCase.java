package com.bukang.product.app;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.bukang.product.domain.RaceSaleStatus;
import com.bukang.product.domain.exception.RacePeriodException;
import com.bukang.product.dto.RaceScheduleDto;
import com.bukang.product.out.RaceRepository;

import lombok.RequiredArgsConstructor;

/**
 * 정산 서비스(E)가 정산 생성 배치에서 부르는 대상 대회 조회 (payout RacePort, PRO-230)
 * - 1차 정산: 환불 마감일이 기간 안인 대회
 * - 최종 정산: 대회일이 기간 안인 대회
 * 정산 배치는 놓친 날을 따라잡으려고 어제까지 7일치를 한 번에 묻는다 (PRO-61). 취소된 대회는 돌려주지 않는다
 */
@Service
@RequiredArgsConstructor
public class RaceScheduleQueryUseCase {
	// 한 번에 조회할 수 있는 최대 일수. 정산 배치는 7일을 묻고, 잘못된 호출이 대량 조회가 되지 않게 막는다
	// (주문 서비스 판매 내역 조회와 같은 상한)
	static final int MAX_PERIOD_DAYS = 31;
	// 정산 대상이 되는 판매 상태. 대회 취소(CANCELED)는 빠진다 (취소 처리는 대회 취소 이벤트로 따로 한다, PRO-34)
	// 판매 중지(STOPPED)는 이미 팔린 참가권이 있어 넣는다. 새 상태가 생기면 여기서 넣을지 정한다
	static final Set<RaceSaleStatus> PAYOUT_TARGET_STATUSES = EnumSet.of(
		RaceSaleStatus.SCHEDULED, RaceSaleStatus.ON_SALE, RaceSaleStatus.STOPPED, RaceSaleStatus.CLOSED);

	private final RaceRepository raceRepository;

	public List<RaceScheduleDto> findByRefundDeadline(LocalDate from, LocalDate to) {
		checkPeriod(from, to);
		return raceRepository.findPayoutTargetsByRefundDeadline(from, to, PAYOUT_TARGET_STATUSES).stream()
			.map(RaceScheduleDto::from)
			.toList();
	}

	// 대회일은 일시로 저장되어 있어, 끝 날의 하루 전체를 넣으려고 다음 날 0시 미만으로 자른다
	public List<RaceScheduleDto> findByRaceDate(LocalDate from, LocalDate to) {
		checkPeriod(from, to);
		return raceRepository.findPayoutTargetsByRaceDate(from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
				PAYOUT_TARGET_STATUSES).stream()
			.map(RaceScheduleDto::from)
			.toList();
	}

	private static void checkPeriod(LocalDate from, LocalDate to) {
		if (from.isAfter(to)) {
			throw new RacePeriodException("조회 시작일이 종료일보다 늦습니다.");
		}
		if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_PERIOD_DAYS) {
			throw new RacePeriodException("대회는 한 번에 %d일까지 조회할 수 있습니다.".formatted(MAX_PERIOD_DAYS));
		}
	}
}
