package com.bukang.cash.app.charge;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.dto.PageDto;
import com.bukang.cash.dto.charge.ChargeReviewDto;
import com.bukang.cash.out.charge.ChargeReviewQueryRepository;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 「고액 충전 확인」 목록 (정책 4장 2차 2-1 「신규 회원 고액 충전 표시」).
 * 가입 24시간 누적 합계는 NewAccountChargeReviewGuard가 표시를 정할 때와 같은 기준(완료된 충전, 요청 시각이 가입 후 24시간 안)으로 센다
 */
@Service
@RequiredArgsConstructor
public class CashChargeReviewQueryUseCase {
	static final int MAX_PAGE_SIZE = 100;

	private final ChargeReviewQueryRepository chargeReviewQueryRepository;

	@Transactional(readOnly = true)
	public PageDto<ChargeReviewDto> findFlagged(int page, int size) {
		// 읽기 전용 관리자 목록이라 범위 밖 값은 거절하지 않고 가까운 값으로 맞춘다
		Page<WalletCharge> flagged = chargeReviewQueryRepository.findFlagged(
			PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)));
		Map<Long, Long> totals = joinWindowTotals(flagged.getContent());
		return PageDto.from(flagged, charge -> new ChargeReviewDto(
			charge.getWallet().getHolder().getId(),
			charge.getWallet().getHolder().getNickname(),
			charge.getWallet().getHolder().getCreateDate(),
			charge.getAmount(),
			charge.getChargeDate(),
			totals.getOrDefault(charge.getWallet().getId(), 0L)));
	}

	// 지갑 ID(= 회원 ID)별 가입 후 24시간 안 완료 충전 합계. 회원마다 따로 묻지 않고 페이지 전체를 한 번에 읽어 나눈다
	private Map<Long, Long> joinWindowTotals(List<WalletCharge> flagged) {
		Map<Long, LocalDateTime> joinedAtByWalletId = new HashMap<>();
		for (WalletCharge charge : flagged) {
			joinedAtByWalletId.put(charge.getWallet().getId(), charge.getWallet().getHolder().getCreateDate());
		}
		if (joinedAtByWalletId.isEmpty()) {
			return Map.of();
		}
		LocalDateTime from = joinedAtByWalletId.values().stream().min(Comparator.naturalOrder()).orElseThrow();
		LocalDateTime to = joinedAtByWalletId.values().stream().max(Comparator.naturalOrder()).orElseThrow()
			.plusHours(NewAccountChargeReviewGuard.WINDOW_HOURS);

		Map<Long, Long> totals = new HashMap<>();
		for (WalletCharge charge : chargeReviewQueryRepository.findCompletedCreatedBetween(
			joinedAtByWalletId.keySet(), from, to)) {
			long walletId = charge.getWallet().getId();
			LocalDateTime joinedAt = joinedAtByWalletId.get(walletId);
			LocalDateTime requestedAt = charge.getCreateDate();
			// 표시할 때의 between과 같게 양 끝을 포함한다
			if (!requestedAt.isBefore(joinedAt)
				&& !requestedAt.isAfter(joinedAt.plusHours(NewAccountChargeReviewGuard.WINDOW_HOURS))) {
				totals.merge(walletId, charge.getAmount(), Long::sum);
			}
		}
		return totals;
	}
}
