package com.bukang.payout.app;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.PayoutClaim;
import com.bukang.payout.domain.PayoutClaimStatus;
import com.bukang.payout.domain.PayoutItemPaging;
import com.bukang.payout.dto.PageDto;
import com.bukang.payout.dto.PayoutAdminClaimsDto;
import com.bukang.payout.dto.PayoutClaimDto;
import com.bukang.payout.dto.PayoutClaimSummaryDto;
import com.bukang.payout.out.PayoutClaimRepository;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 회수 관리 화면 조회: 카드 숫자와 청구 목록 (BUK-107)
 * 「회수 완료」 버튼은 이미 있는 청구 입금 확인 API를 쓴다
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayoutClaimQueryUseCase {
	private final PayoutClaimRepository payoutClaimRepository;

	// code를 주면 그 한 건만 같은 페이지 모양(0 ~ 1건)으로 돌려주고 status는 보지 않는다
	public PayoutAdminClaimsDto claims(PayoutClaimStatus status, String code, int page, int size, LocalDate today) {
		PayoutItemPaging.check(page, size);
		PageRequest pageable = PageRequest.of(page, size);
		Page<PayoutClaim> claims;
		if (code != null && !code.isBlank()) {
			List<PayoutClaim> found = payoutClaimRepository.findByClaimCode(code.strip().toUpperCase(Locale.ROOT))
				.stream()
				.toList();
			claims = new PageImpl<>(found, pageable, found.size());
		} else if (status != null) {
			claims = payoutClaimRepository.findByStatusOrderByIdDesc(status, pageable);
		} else {
			claims = payoutClaimRepository.findAllByOrderByIdDesc(pageable);
		}
		return new PayoutAdminClaimsDto(summary(today), PageDto.from(claims, PayoutClaimDto::from));
	}

	private PayoutClaimSummaryDto summary(LocalDate today) {
		LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
		return new PayoutClaimSummaryDto(
			payoutClaimRepository.countByStatus(PayoutClaimStatus.청구_중),
			payoutClaimRepository.sumAmountByStatus(PayoutClaimStatus.청구_중),
			payoutClaimRepository.countByConfirmedAtGreaterThanEqualAndConfirmedAtLessThan(
				monthStart, monthStart.plusMonths(1)));
	}
}
