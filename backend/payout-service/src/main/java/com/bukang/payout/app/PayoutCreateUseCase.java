package com.bukang.payout.app;

import static java.util.stream.Collectors.groupingBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutTargetDate;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.PayoutSourcePort;
import com.bukang.payout.out.SoldEntry;

import lombok.RequiredArgsConstructor;

/**
 * 정산 대상일 배치: 오늘이 정산 대상일인 대회의 정산 내역(판매자 × 대회 × 월)과 정산 항목을 만든다 (payout.md 2-3, 2-6)
 */
@Service
@RequiredArgsConstructor
public class PayoutCreateUseCase {
	// 플랫폼 수수료율 5.5% (부가세 포함). 정산 항목마다 기록 시점 값으로 남긴다
	private static final BigDecimal FEE_RATE = new BigDecimal("0.0550");

	private final PayoutRepository payoutRepository;
	private final PayoutSourcePort payoutSourcePort;
	private final BusinessDayCalendar businessDayCalendar;

	@Transactional
	public void run(LocalDate targetDate, LocalDateTime now) {
		LocalDate raceDate = PayoutTargetDate.raceDateFor(targetDate);
		YearMonth payoutMonth = YearMonth.from(targetDate);

		Map<List<Integer>, List<SoldEntry>> bySellerAndRace = payoutSourcePort.findSoldEntries(raceDate).stream()
			.collect(groupingBy(entry -> List.of(entry.sellerId(), entry.raceId())));

		bySellerAndRace.forEach((key, entries) -> {
			int sellerId = key.get(0);
			int raceId = key.get(1);

			// 같은 날 배치가 다시 돌아도 이미 만든 묶음은 건너뛴다 (중복 지급 방지, DB 유일 제약이 마지막 방어선)
			if (payoutRepository.existsBySellerIdAndRaceIdAndPayoutMonth(sellerId, raceId, payoutMonth.toString())) {
				return;
			}

			Payout payout = new Payout(sellerId, raceId, payoutMonth, now, businessDayCalendar);
			entries.forEach(entry -> payout.addItem(entry.orderItemId(), entry.price(), FEE_RATE, targetDate));

			// 정산 ID의 끝 4자리는 PK에서 만들므로 저장해서 ID를 받은 뒤에 붙인다
			payoutRepository.save(payout);
			payout.assignPayoutCode();
		});
	}
}
