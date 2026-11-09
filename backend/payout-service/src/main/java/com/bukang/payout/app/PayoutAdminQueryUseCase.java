package com.bukang.payout.app;

import java.time.LocalDate;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.PayoutCreateFailureType;
import com.bukang.payout.domain.PayoutItemPaging;
import com.bukang.payout.domain.PayoutQueryPeriod;
import com.bukang.payout.dto.PageDto;
import com.bukang.payout.dto.PayoutCreateFailureDto;
import com.bukang.payout.dto.PayoutCreateFailuresDto;
import com.bukang.payout.out.PayoutCreateFailureRepository;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 정산 조회. 관리자인지는 PayoutAdminFacade가 먼저 확인한다
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayoutAdminQueryUseCase {
	private final PayoutCreateFailureRepository payoutCreateFailureRepository;

	// 이상 감지 기록 (BUK-108). 기간은 배치 실행일 기준이다. type이 null이면 모든 종류를 보여 준다
	public PayoutCreateFailuresDto createFailures(LocalDate from, LocalDate to, PayoutCreateFailureType type,
		int page, int size) {
		PayoutQueryPeriod.checkFailureDays(from, to);
		PayoutItemPaging.check(page, size);
		return new PayoutCreateFailuresDto(
			payoutCreateFailureRepository.countByFailureTypeAndRunDateBetween(PayoutCreateFailureType.대사_불일치, from, to),
			payoutCreateFailureRepository.countAutoFilled(from, to),
			payoutCreateFailureRepository.countByFailureTypeAndRunDateBetween(PayoutCreateFailureType.주문_조회_실패, from, to),
			PageDto.from(payoutCreateFailureRepository.findAdminRecords(from, to, type, PageRequest.of(page, size)),
				PayoutCreateFailureDto::from));
	}
}
