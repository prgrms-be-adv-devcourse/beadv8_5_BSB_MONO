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
import com.bukang.payout.domain.PayoutHold;
import com.bukang.payout.domain.PayoutItem;
import com.bukang.payout.domain.PayoutRecordType;
import com.bukang.payout.domain.PayoutRecovery;
import com.bukang.payout.domain.PayoutRecoveryReason;
import com.bukang.payout.domain.PayoutRecoveryStatus;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.domain.exception.PayoutClaimConflictException;
import com.bukang.payout.domain.exception.PayoutClaimNotFoundException;
import com.bukang.payout.out.ClaimVirtualAccountPort;
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
 *
 * 가상계좌 (BUK-88): 청구마다 캐시에 전용 가상계좌를 발급받고, 판매자가 입금하면 캐시가 보낸 입금 메시지로
 * 관리자 확인 없이 회수를 끝낸다. 메시지가 빠질 때를 대비해 관리자 확인도 남긴다. 먼저 온 쪽만 회수를 끝낸다
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
	private final ClaimVirtualAccountPort claimVirtualAccountPort;

	/**
	 * 하루 한 번 청구를 만들고 알림을 남긴다. 같은 날 다시 돌아도 청구와 알림은 한 번만 남는다
	 */
	@Transactional
	public ClaimRunResult run(LocalDate today, LocalDateTime now) {
		int created = createClaims(today);
		int sellerNotices = 0;
		int adminNotices = 0;
		for (PayoutClaim claim : payoutClaimRepository.findByStatusOrderByIdAsc(PayoutClaimStatus.청구_중)) {
			issueVirtualAccount(claim);
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
		finishRecovery(claim, confirmedBy, now);
		return claim;
	}

	/**
	 * 입금 메시지의 회수 확인: 판매자가 청구 전용 가상계좌로 입금했다 (BUK-88)
	 * 같은 메시지가 다시 오거나 관리자가 먼저 확인했으면 건너뛴다. 청구 행을 잠가 동시에 와도 한쪽만 처리하고,
	 * 회수 입금 줄의 유일 제약(bundle_key C{청구}:회수_입금)이 마지막으로 두 번 기록을 막는다
	 * 입금액이 청구 금액과 다르거나 갚을 돈이 바뀌었으면 거부한다. 메시지는 DLT로 가고 사람이 맞춘다
	 */
	@Transactional
	public void confirmByDeposit(String claimCode, long depositAmount, LocalDateTime depositedAt) {
		PayoutClaim claim = payoutClaimRepository.findByClaimCodeForUpdate(claimCode)
			.orElseThrow(PayoutClaimNotFoundException::new);
		if (claim.getStatus() != PayoutClaimStatus.청구_중) {
			skipConfirmed(claim, depositAmount, depositedAt);
			return;
		}
		if (depositAmount != claim.getAmount()) {
			log.error("입금액이 청구 금액과 달라 회수를 끝내지 않습니다. 사람이 확인해야 합니다. "
					+ "claimCode={}, sellerId={}, raceId={}, 청구 금액={}, 입금액={}", claimCode, claim.getSellerId(),
				claim.getRaceId(), claim.getAmount(), depositAmount);
			throw new PayoutClaimConflictException("입금액이 청구 금액과 다릅니다.");
		}
		checkReceivableMatches(claim);
		finishRecovery(claim, PayoutHold.SYSTEM_MEMBER_ID, depositedAt);
	}

	// 이미 확인한 청구에 입금 메시지가 왔다. 시스템이 확인한 청구면 같은 메시지가 다시 온 것이라 INFO로 넘긴다
	// 관리자가 확인한 청구면 판매자가 목업 계좌와 가상계좌로 두 번 보냈을 수 있어 WARN으로 남긴다 (돌려줄 돈은 사람이 확인)
	private void skipConfirmed(PayoutClaim claim, long depositAmount, LocalDateTime depositedAt) {
		if (claim.getConfirmedBy() == PayoutHold.SYSTEM_MEMBER_ID) {
			log.info("이미 입금을 확인한 청구라 입금 메시지를 건너뜁니다. claimCode={}", claim.getClaimCode());
			return;
		}
		log.warn("관리자가 이미 입금을 확인한 청구에 가상계좌 입금이 왔습니다. 판매자가 두 번 보냈을 수 있어 확인이 필요합니다. "
				+ "claimCode={}, sellerId={}, raceId={}, 입금액={}, depositedAt={}, confirmedBy={}, confirmedAt={}",
			claim.getClaimCode(), claim.getSellerId(), claim.getRaceId(), depositAmount, depositedAt,
			claim.getConfirmedBy(), claim.getConfirmedAt());
	}

	// 청구를 입금 확인으로 바꾸고, 청구 금액만큼 회수 입금 줄을 남기고, 그 대회의 요청 중인 회수를 입금으로 끝낸다
	private void finishRecovery(PayoutClaim claim, long confirmedBy, LocalDateTime confirmedAt) {
		claim.confirm(confirmedBy, confirmedAt);
		payoutItemRepository.save(PayoutItem.ofClaim(claim, PayoutRecordType.회수_입금, -claim.getAmount()));
		payoutRecoveryRepository.findBySellerIdAndRaceIdAndStatus(claim.getSellerId(), claim.getRaceId(),
				PayoutRecoveryStatus.회수_요청)
			.forEach(recovery -> recovery.complete(confirmedBy, confirmedAt));
	}

	// 아직 가상계좌가 없는 청구에 발급을 요청한다. 실패하면 로그만 남기고 다음 배치에서 다시 요청한다
	// 청구 ID가 없는 예전 청구는 캐시가 입금을 맞출 키가 없어 발급하지 않는다 (관리자 확인으로 끝낸다)
	// shortcut: 배치 트랜잭션 안에서 발급 포트를 부른다. 진짜 어댑터는 @Transactional 없이 HTTP만 부르고
	// 응답 제한 시간을 짧게 둔다. 청구가 많아 배치가 길어지면 지급처럼 호출을 트랜잭션 밖으로 뺀다
	private void issueVirtualAccount(PayoutClaim claim) {
		if (claim.getVirtualAccountNumber() != null || claim.getClaimCode() == null) {
			return;
		}
		try {
			claimVirtualAccountPort.issue(claim.getClaimCode(), claim.getAmount(), claim.getDueDate())
				.ifPresent(account -> claim.assignVirtualAccount(account.bankName(), account.accountNumber()));
		} catch (RuntimeException e) {
			log.warn("가상계좌 발급에 실패했습니다. 다음 배치에서 다시 요청합니다. claimCode={}", claim.getClaimCode(), e);
		}
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
				startDate.get())).assignClaimCode(today);
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
