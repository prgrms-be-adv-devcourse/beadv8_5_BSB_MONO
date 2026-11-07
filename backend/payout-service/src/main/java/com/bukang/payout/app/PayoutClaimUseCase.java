package com.bukang.payout.app;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutAccount;
import com.bukang.payout.domain.PayoutClaim;
import com.bukang.payout.domain.PayoutClaimStatus;
import com.bukang.payout.domain.PayoutItem;
import com.bukang.payout.domain.PayoutRecordType;
import com.bukang.payout.domain.PayoutRecovery;
import com.bukang.payout.domain.PayoutRecoveryReason;
import com.bukang.payout.domain.PayoutRecoveryStatus;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.domain.exception.PayoutClaimConflictException;
import com.bukang.payout.domain.exception.PayoutClaimNotFoundException;
import com.bukang.payout.out.PayoutClaimRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;
import com.bukang.payout.out.PayoutRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 청구: 상계로도 못 갚은 돈을 판매자에게 직접 보내 달라고 요청하고, 관리자가 입금을 확인한다
 * (선 환불 후 회수 R3 · R4 · R5 · R10, 피그잼 06-1 [6. 판매자 정산 / 3-1 ~ 3])
 *
 * 매일 04:00 지급 배치와 함께 돈다
 * 1. 청구 만들기: 갚을 돈이 남은 판매자 × 대회 중 최종 정산을 만든 곳(상계가 끝남), 대회 취소는 취소 다음 날부터
 *    최종 정산 전에는 상계로 금액이 바뀌므로 만들지 않는다
 * 2. 판매자 알림: 입금 확인 전까지 매일 한 번, 은행별 입금 계좌와 금액을 알린다
 * 3. 관리자 알림: 상계 실패일을 1일째로 셀 때 30일째에 한 번 알린다
 * 알림 기능이 아직 없어 로그만 남긴다. 알림 기능이 생기면 여기서 보낸다
 *
 * 관리자가 입금을 확인하면 청구 금액이 회사 계좌에 들어왔다고 보고 원장에 회수 입금을 남긴다.
 * cash 보관 지갑은 건드리지 않는다 (10/30 결정)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutClaimUseCase {
	// 판매자가 돈을 보낼 은행별 입금 계좌 (목업). 청구마다 같은 계좌를 알린다
	static final List<String> RECOVERY_ACCOUNTS = List.of(
		"국민은행 000000-00-000000 크루런(목업)",
		"신한은행 000-000-000000 크루런(목업)",
		"우리은행 0000-000-000000 크루런(목업)");

	private final PayoutRepository payoutRepository;
	private final PayoutItemRepository payoutItemRepository;
	private final PayoutRecoveryRepository payoutRecoveryRepository;
	private final PayoutClaimRepository payoutClaimRepository;

	/**
	 * 하루 한 번 청구를 만들고 알림을 남긴다. 같은 날 다시 돌아도 청구와 알림은 한 번만 남는다
	 */
	@Transactional
	public ClaimRunResult run(LocalDate today, LocalDateTime now) {
		int created = createClaims(today);
		int sellerNotices = 0;
		int adminNotices = 0;
		for (PayoutClaim claim : payoutClaimRepository.findByStatusOrderByIdAsc(PayoutClaimStatus.청구_중)) {
			if (claim.markSellerNotice(today)) {
				sellerNotices++;
				log.info("[청구 알림] 판매자에게 정산금 반환을 요청합니다. sellerId={}, raceId={}, claimId={}, amount={}, accounts={}",
					claim.getSellerId(), claim.getRaceId(), claim.getId(), claim.getAmount(), RECOVERY_ACCOUNTS);
			}
			if (claim.markAdminNotice(today, now)) {
				adminNotices++;
				log.warn("[관리자 알림] 청구하고 30일이 지나도 판매자가 돌려주지 않았습니다. "
					+ "sellerId={}, raceId={}, claimId={}, amount={}, startDate={}", claim.getSellerId(),
					claim.getRaceId(), claim.getId(), claim.getAmount(), claim.getStartDate());
			}
		}
		log.info("청구 배치: 새 청구 {}건, 판매자 알림 {}건, 관리자 알림 {}건", created, sellerNotices, adminNotices);
		return new ClaimRunResult(created, sellerNotices, adminNotices);
	}

	/**
	 * 관리자의 회수 확인: 판매자가 청구 금액을 보냈다고 확인한다 (R4 · R5)
	 * 청구 금액만큼 회수 입금 줄을 남기고, 그 대회의 요청 중인 회수를 입금으로 끝낸다
	 * 그 대회의 갚을 돈이 청구 금액과 다르면 확인하지 않는다 (PR #265 리뷰). 청구는 상계가 끝난 뒤 만들어 금액이
	 * 바뀌지 않아야 하는데, 바뀌었다면 청구액만 받고 회수를 모두 끝내 갚을 돈이 남거나 두 번 받은 것으로 적힌다
	 * 거부된 청구는 그대로 두고, 로그를 보고 사람이 맞춘다 (11/2 결정)
	 */
	@Transactional
	public PayoutClaim confirm(long claimId, long confirmedBy, LocalDateTime now) {
		PayoutClaim claim = payoutClaimRepository.findByIdForUpdate(claimId)
			.orElseThrow(PayoutClaimNotFoundException::new);
		// 이미 확인한 청구는 갚을 돈이 0이라 금액 비교보다 "이미 확인함"을 먼저 알린다 (claim.confirm)
		if (claim.getStatus() == PayoutClaimStatus.청구_중) {
			checkReceivableMatches(claim);
		}
		claim.confirm(confirmedBy, now);
		payoutItemRepository.save(PayoutItem.ofClaim(claim, PayoutRecordType.회수_입금, -claim.getAmount()));
		payoutRecoveryRepository.findBySellerIdAndRaceIdAndStatus(claim.getSellerId(), claim.getRaceId(),
				PayoutRecoveryStatus.회수_요청)
			.forEach(recovery -> recovery.complete(confirmedBy, now));
		return claim;
	}

	private void checkReceivableMatches(PayoutClaim claim) {
		long receivable = payoutItemRepository.sumBalance(claim.getSellerId(), claim.getRaceId(),
			PayoutAccount.갚을_돈);
		if (receivable == claim.getAmount()) {
			return;
		}
		log.error("청구 금액과 갚을 돈이 달라 회수 확인을 거부합니다. 청구 뒤 갚을 돈이 바뀌었습니다. "
				+ "claimId={}, sellerId={}, raceId={}, 청구 금액={}, 지금 갚을 돈={}, 차이={}",
			claim.getId(), claim.getSellerId(), claim.getRaceId(), claim.getAmount(), receivable,
			receivable - claim.getAmount());
		throw new PayoutClaimConflictException("예상치 못한 일이 발생해 회수를 확인할 수 없습니다. 관리자에게 문의해 주세요.");
	}

	// 요청 중인 회수가 있는 판매자 × 대회마다 청구를 만들 때가 됐는지 본다
	private int createClaims(LocalDate today) {
		Set<SellerRace> targets = new LinkedHashSet<>();
		payoutRecoveryRepository.findByStatus(PayoutRecoveryStatus.회수_요청)
			.forEach(recovery -> targets.add(new SellerRace(recovery.getSellerId(), recovery.getRaceId())));
		int created = 0;
		for (SellerRace target : targets) {
			if (payoutClaimRepository.existsBySellerIdAndRaceId(target.sellerId(), target.raceId())) {
				continue;
			}
			Optional<LocalDate> startDate = claimStartDate(target, today);
			long receivable = payoutItemRepository.sumBalance(target.sellerId(), target.raceId(),
				PayoutAccount.갚을_돈);
			if (startDate.isEmpty() || receivable <= 0) {
				continue;
			}
			payoutClaimRepository.save(new PayoutClaim(target.sellerId(), target.raceId(), receivable,
				startDate.get()));
			created++;
		}
		return created;
	}

	// 청구를 시작하는 날(상계 실패일). 아직 청구할 때가 아니면 빈 값
	// - 최종 정산을 만들었으면 그날: 상계가 끝나 더는 금액이 바뀌지 않는다
	// - 대회가 취소됐으면 취소일. 단 취소 다음 날 배치부터 만든다
	private Optional<LocalDate> claimStartDate(SellerRace target, LocalDate today) {
		Optional<Payout> finalPayout = payoutRepository.findFirstBySellerIdAndRaceIdAndPayoutStageOrderByIdAsc(
				target.sellerId(), target.raceId(), PayoutStage.최종_정산)
			.filter(payout -> payout.getStatus() != PayoutStatus.취소);
		if (finalPayout.isPresent()) {
			return Optional.of(finalPayout.get().getSettledAt().toLocalDate());
		}
		return payoutRecoveryRepository.findFirstBySellerIdAndRaceIdAndReasonType(target.sellerId(),
				target.raceId(), PayoutRecoveryReason.대회_취소)
			.map(PayoutRecovery::getRequestedAt)
			.map(LocalDateTime::toLocalDate)
			.filter(canceledDate -> today.isAfter(canceledDate));
	}

	private record SellerRace(long sellerId, long raceId) {
	}

	/**
	 * @param created 이번에 만든 청구 수
	 * @param sellerNotices 이번에 남긴 판매자 청구 알림 수
	 * @param adminNotices 이번에 남긴 관리자 알림 수
	 */
	public record ClaimRunResult(int created, int sellerNotices, int adminNotices) {
	}
}
