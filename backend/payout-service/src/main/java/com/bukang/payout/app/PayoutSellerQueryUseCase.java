package com.bukang.payout.app;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutItemPaging;
import com.bukang.payout.domain.PayoutQueryPeriod;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.dto.PageDto;
import com.bukang.payout.dto.PayoutDto;
import com.bukang.payout.dto.PayoutItemDto;
import com.bukang.payout.dto.PayoutSummaryDto;
import com.bukang.payout.dto.SellerPayoutHoldDto;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRepository;

import lombok.RequiredArgsConstructor;

/**
 * 판매자의 정산 조회 (payout.md 1장 조회 · 보관)
 * 모든 조회는 판매자 ID로 범위를 묶어, 다른 판매자의 정산은 ID를 알아도 보이지 않는다
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayoutSellerQueryUseCase {
	private final PayoutRepository payoutRepository;
	private final PayoutItemRepository payoutItemRepository;
	private final PayoutHoldRepository payoutHoldRepository;

	public List<PayoutDto> payouts(long sellerId, YearMonth fromMonth, YearMonth toMonth) {
		PayoutQueryPeriod.checkPayoutMonths(fromMonth, toMonth);
		return payoutRepository
			.findBySellerIdAndPayoutMonthBetweenOrderByPayoutMonthDescIdDesc(
				sellerId, fromMonth.toString(), toMonth.toString())
			.stream()
			.map(PayoutDto::from)
			.toList();
	}

	// 정렬은 쿼리에 고정(정산 대상일 최근 순)했으므로 Pageable에는 정렬을 넣지 않는다
	public PageDto<PayoutItemDto> items(long sellerId, LocalDate from, LocalDate to, int page, int size) {
		PayoutQueryPeriod.checkDetailDays(from, to);
		PayoutItemPaging.check(page, size);
		return PageDto.from(
			payoutItemRepository.findSellerItems(sellerId, from, to, PageRequest.of(page, size)),
			PayoutItemDto::from);
	}

	public List<SellerPayoutHoldDto> holds(long sellerId, LocalDate from, LocalDate to) {
		PayoutQueryPeriod.checkDetailDays(from, to);
		return payoutHoldRepository.findSellerHolds(sellerId, from.atStartOfDay(), to.plusDays(1).atStartOfDay())
			.stream()
			.map(SellerPayoutHoldDto::from)
			.toList();
	}

	public PayoutSummaryDto summary(long sellerId, LocalDate today) {
		// 지급 배치가 실제로 지급할 정산 예정 내역만 본다. 1차를 기다리는 최종은 지급되지 않으므로 뺀다 (PRO-188)
		// 지급 예정일이 지났는데 아직 CALCULATED라면(배치 실행을 놓침) 그 날짜를 그대로 보여 준다. 다음 배치에서 지급된다
		// 지급 예정 정산 내역이 없으면 null이다. 지급일이 대회마다 달라(기준일 + 2일) 대신 채울 정기 지급일이 없다 (10/28 결정)
		List<Payout> payable = payoutRepository.findPayableForSummary(sellerId, PayoutStatus.CALCULATED,
			PayoutStage.FINAL, PayoutStage.FIRST, PayoutStatus.PAID, PayoutStatus.CANCELED);
		LocalDate nextPayDate = payable.isEmpty() ? null : payable.getFirst().getScheduledPayDate();

		// 정산 예정액: 다음 정산일에 나갈 지급액 합계
		long scheduledPayoutAmount = payable.stream()
			.filter(payout -> payout.getScheduledPayDate().equals(nextPayDate))
			.mapToLong(Payout::getPayoutAmount)
			.sum();
		long heldCount = payoutRepository.countBySellerIdAndStatus(sellerId, PayoutStatus.HELD);
		return new PayoutSummaryDto(nextPayDate, scheduledPayoutAmount, heldCount);
	}
}
