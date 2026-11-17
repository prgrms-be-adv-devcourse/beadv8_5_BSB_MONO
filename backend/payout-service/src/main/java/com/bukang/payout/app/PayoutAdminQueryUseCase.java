package com.bukang.payout.app;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutCreateFailureType;
import com.bukang.payout.domain.PayoutHold;
import com.bukang.payout.domain.PayoutHoldStatus;
import com.bukang.payout.domain.PayoutItemPaging;
import com.bukang.payout.domain.PayoutQueryPeriod;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.dto.PageDto;
import com.bukang.payout.dto.PayoutAdminHoldDto;
import com.bukang.payout.dto.PayoutAdminHoldsDto;
import com.bukang.payout.dto.PayoutAdminPayoutDto;
import com.bukang.payout.dto.PayoutAdminPayoutsDto;
import com.bukang.payout.dto.PayoutCreateFailureDto;
import com.bukang.payout.dto.PayoutCreateFailuresDto;
import com.bukang.payout.dto.PayoutHoldDto;
import com.bukang.payout.dto.PayoutHoldSummaryDto;
import com.bukang.payout.dto.PayoutStatusSummaryDto;
import com.bukang.payout.out.PayoutCreateFailureRepository;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutRepository;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 정산 조회. 관리자인지는 PayoutAdminFacade가 먼저 확인한다
 * open-in-view가 꺼져 있어 응답 DTO는 이 트랜잭션 안에서 만든다
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayoutAdminQueryUseCase {
	private final PayoutCreateFailureRepository payoutCreateFailureRepository;
	private final PayoutHoldRepository payoutHoldRepository;
	private final PayoutRepository payoutRepository;

	// 이상 감지 기록 (BUK-108). 기간은 배치 실행일 기준이다. type이 null이면 모든 종류를 보여 준다
	public PayoutCreateFailuresDto createFailures(LocalDate from, LocalDate to, PayoutCreateFailureType type,
		int page, int size) {
		PayoutQueryPeriod.checkFailureDays(from, to);
		PayoutItemPaging.check(page, size);
		return new PayoutCreateFailuresDto(
			count(PayoutCreateFailureType.대사_불일치, from, to),
			payoutCreateFailureRepository.countAutoFilled(from, to),
			count(PayoutCreateFailureType.주문_조회_실패, from, to),
			PageDto.from(payoutCreateFailureRepository.findAdminRecords(from, to, type, PageRequest.of(page, size)),
				PayoutCreateFailureDto::from));
	}

	private long count(PayoutCreateFailureType type, LocalDate from, LocalDate to) {
		return payoutCreateFailureRepository.countByFailureTypeAndRunDateBetween(type, from, to);
	}

	// 지급보류 목록 (BUK-168). code(HD- 또는 ST-)를 주면 찾은 보류만 같은 페이지 모양으로 돌려주고 status는 보지 않는다
	// (청구 목록의 ID 찾기와 같은 방식). 카드는 필터와 상관없이 전체로 센다
	public PayoutAdminHoldsDto holds(PayoutHoldStatus status, String code, int page, int size, LocalDate today) {
		PayoutItemPaging.check(page, size);
		Page<PayoutHold> holds;
		if (code != null && !code.isBlank()) {
			List<PayoutHold> found = payoutHoldRepository.findAdminHoldsByCode(code.strip().toUpperCase(Locale.ROOT));
			// 찾기는 늘 첫 페이지다. page가 1 이상이면 PageImpl이 총 개수를 틀리게 맞춘다 (#368 리뷰)
			holds = new PageImpl<>(found, PageRequest.of(0, size), found.size());
		} else {
			holds = payoutHoldRepository.findAdminHolds(status, PageRequest.of(page, size));
		}
		return new PayoutAdminHoldsDto(holdSummary(today), PageDto.from(holds, PayoutAdminHoldDto::from));
	}

	private PayoutHoldSummaryDto holdSummary(LocalDate today) {
		LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
		return new PayoutHoldSummaryDto(
			payoutHoldRepository.countByStatus(PayoutHoldStatus.지급보류),
			payoutHoldRepository.sumHeldPayoutAmount(),
			payoutHoldRepository.countByReleasedAtGreaterThanEqualAndReleasedAtLessThan(
				monthStart, monthStart.plusMonths(1)));
	}

	// 정산 목록 (BUK-168): 지급 예정일 하루의 정산. 진행 중 보류는 페이지의 정산들을 모아 한 번에 읽는다
	public PayoutAdminPayoutsDto payouts(LocalDate payDate, PayoutStatus status, int page, int size) {
		PayoutItemPaging.check(page, size);
		Page<Payout> payouts = payoutRepository.findAdminPayouts(payDate, status, PageRequest.of(page, size));
		Map<Long, List<PayoutHoldDto>> holds = payoutHoldRepository.findByPayoutIdInAndStatus(
				payouts.map(Payout::getId).getContent(), PayoutHoldStatus.지급보류).stream()
			.collect(groupingBy(hold -> hold.getPayout().getId(), mapping(PayoutHoldDto::from, toList())));
		return new PayoutAdminPayoutsDto(payDate, statusSummary(payDate), PageDto.from(payouts,
			payout -> PayoutAdminPayoutDto.of(payout, holds.getOrDefault(payout.getId(), List.of()))));
	}

	// 정산이 없는 상태도 0으로 채워 카드 칸이 늘 같게 한다
	private List<PayoutStatusSummaryDto> statusSummary(LocalDate payDate) {
		Map<PayoutStatus, PayoutStatusSummaryDto> sums = payoutRepository.sumByStatusOn(payDate).stream()
			.collect(toMap(PayoutStatusSummaryDto::status, Function.identity()));
		return Arrays.stream(PayoutStatus.values())
			.map(status -> sums.getOrDefault(status, new PayoutStatusSummaryDto(status, 0, 0)))
			.toList();
	}
}
