package com.bukang.payout.app;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutAccount;
import com.bukang.payout.domain.PayoutItem;
import com.bukang.payout.domain.PayoutRecordType;
import com.bukang.payout.domain.PayoutRecovery;
import com.bukang.payout.domain.PayoutRecoveryReason;
import com.bukang.payout.domain.PayoutRecoveryStatus;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.domain.TwoStagePayoutCalculator;
import com.bukang.payout.domain.exception.PayoutRecoveryConflictException;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;
import com.bukang.payout.out.PayoutRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 원장 잔고와 회수 상계 (PRO-214)
 * 잔고는 판매자 × 대회 × 계정의 원장 줄을 더해 계산한다. 따로 저장하지 않는다
 * 상계는 갚을 돈을 같은 판매자 × 대회의 지급 전 정산 내역에서 바로 뺀다. 정산 내역 금액까지만 뺀다
 * 회수 요청 · 최종 생성 · 대회 취소가 같은 규칙을 쓰도록 한곳에 둔다
 * 참가권 환불(일정 변경 · 대회 취소)의 회수 요청과 관리자의 입금 확인도 여기서 받는다 (payout.md 4-2 · 4-3, BUK-76)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutLedgerUseCase {
	private final PayoutRepository payoutRepository;
	private final PayoutItemRepository payoutItemRepository;
	private final PayoutRecoveryRepository payoutRecoveryRepository;

	/**
	 * 같은 판매자 × 대회의 정산 내역을 상태와 상관없이 잠근다. 회수와 최종 정산 생성이 시작할 때 부른다 (BUK-52)
	 * 둘이 겹치면 최종 생성이 아직 커밋되지 않은 회수의 1차 정산금 차감을 못 보고 전액을 또 빼서 판매자 몫이 두 번 깎인다
	 * 먼저 잠근 쪽이 끝난 뒤에 다른 쪽이 시작하므로 한 번에 하나만 돈다. 1차 정산이 이미 지급 완료여도 잠근다
	 * 트랜잭션의 첫 문장이어야 한다. 일반 조회가 먼저 나가면 잠금을 기다린 뒤에도 옛 값을 읽을 수 있다
	 * 1차 정산이 없으면 잠글 행이 없다. 그때는 1차 정산금이 없어 겹쳐도 깎을 몫이 없다
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void lockSellerRace(long sellerId, long raceId) {
		payoutRepository.findForUpdate(sellerId, raceId, EnumSet.allOf(PayoutStatus.class));
	}

	/**
	 * 참가권 환불의 회수 요청 (payout.md 4-2 · 4-3, PRO-33 · PRO-214, BUK-76)
	 * 일정 변경 · 대회 취소 모두 주문이 환불을 끝낸 참가권마다 한 건씩 만든다 (선 환불 후 회수)
	 * 1차 정산을 만든 뒤의 환불은 1차 지급 전 · 뒤를 나누지 않고 항상 회수 요청으로 기록한다
	 * 회수액은 그 참가권의 1차 몫(가격 × 40%, 원 미만 버림)이다. 가격은 주문 쪽 값을 받는다
	 * 요청하면 같은 판매자 × 대회의 지급 전 정산 내역에서 바로 상계하고, 다 갚으면 회수가 바로 끝난다
	 * 1차가 지급 중이면 상계할 곳이 없어 요청으로 남는다. 지급이 거절돼 1차가 닫히면 대회 취소 되돌리기가 지운다
	 *
	 * @param raceCourseId 환불된 참가권의 코스 (원장 줄에 적어 코스별로 본다)
	 * @param price 환불된 참가권의 가격 (주문 상품 가격)
	 * @param reason 회수 사유 (일정 변경 / 대회 취소)
	 * @return 만든 회수. 1차 정산이 없으면(1차 정산금을 준 적이 없으면) 돌려받을 것이 없어 빈 값
	 */
	@Transactional
	public Optional<PayoutRecovery> recoverRefund(long sellerId, long raceId, long raceCourseId,
		long orderItemId, long price, PayoutRecoveryReason reason, LocalDateTime now) {
		lockSellerRace(sellerId, raceId);
		// 같은 환불 알림이 다시 와도(Kafka 재전달) 실패로 보지 않고 이미 만든 회수를 돌려준다.
		// 409로 던지면 소비자가 재시도하다 DLT로 보내 이미 처리한 환불이 실패로 쌓인다 (R1)
		Optional<PayoutRecovery> existing = payoutRecoveryRepository.findByOrderItemId(orderItemId);
		if (existing.isPresent()) {
			return existing;
		}
		if (findLive(sellerId, raceId, PayoutStage.최종_정산).isPresent()) {
			// 최종 정산을 만든 뒤의 환불은 번 돈까지 되돌려야 해서 아직 정하지 않았다 (PRO-214 미룸)
			// 주문이 정산 생성일부터 환불을 막으므로 생기면 안 되는 경우다. 구매자 환불은 이미 끝났으니
			// 사람이 판매자에게서 돌려받을 수 있게 처리하는 자리에서 자세히 남긴다 (PR #265 리뷰)
			log.error("최종 정산 뒤 환불이라 회수를 남기지 못했습니다. 사람이 확인해야 합니다. "
				+ "sellerId={}, raceId={}, orderItemId={}, price={}", sellerId, raceId, orderItemId, price);
			throw new PayoutRecoveryConflictException("최종 정산을 만든 뒤의 환불은 아직 회수할 수 없습니다.");
		}
		if (findLive(sellerId, raceId, PayoutStage.일차_정산).isEmpty()) {
			return Optional.empty();
		}

		long amount = TwoStagePayoutCalculator.firstShare(price);
		PayoutRecovery recovery = reason == PayoutRecoveryReason.대회_취소
			? PayoutRecovery.raceCanceled(sellerId, raceId, orderItemId, amount, now)
			: PayoutRecovery.scheduleChanged(sellerId, raceId, orderItemId, amount, now);
		PayoutRecovery opened = openRecovery(recovery, orderItemId, raceCourseId);
		settle(sellerId, raceId, now);
		return Optional.of(opened);
	}

	/**
	 * 1차 정산 내역이 돈 없이 닫힌 뒤(대회 취소로 지급 전 취소, 지급 거절), 그 판매자 × 대회의 회수를 되돌린다 (R9, BUK-76)
	 * 1차 정산 내역의 상계 줄은 정산 내역 취소로 되돌아가지만 회수 줄(요청 · 갚을 돈 추가)은 회수에 붙어 남는다.
	 * 그대로 두면 판매자가 받은 적 없는 몫이 1차 정산금 잔고 −, 갚을 돈 +로 남아 청구된다
	 * 사유(일정 변경 · 대회 취소)와 상관없이 요청 중이거나 상계로 끝난 회수를 모두 되돌린다.
	 * 환불 알림이 대회 취소보다 먼저 와서 상계됐거나, 1차 지급 중에 요청으로 남은 회수도 여기서 지운다
	 * 최종 정산이 없을 때만 부른다. 그때 판매자가 받은 정산금은 없다
	 * 입금으로 끝난 회수는 판매자가 낸 돈이라 되돌리지 않는다 (남은 위험, BUK-76)
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public List<PayoutRecovery> revertRecoveries(long sellerId, long raceId) {
		List<PayoutRecovery> reverted = payoutRecoveryRepository.findForUpdateBySellerIdAndRaceIdAndStatusIn(
				sellerId, raceId, EnumSet.of(PayoutRecoveryStatus.회수_요청, PayoutRecoveryStatus.회수_완료)).stream()
			.filter(PayoutRecovery::isRevertibleByRaceCancel)
			.toList();
		for (PayoutRecovery recovery : reverted) {
			long amount = recovery.getAmount();
			Long orderItemId = recovery.getOrderItemId();
			payoutItemRepository.save(PayoutItem.ofRecovery(recovery, PayoutRecordType.회수_요청_취소,
				orderItemId, null, amount));
			payoutItemRepository.save(PayoutItem.ofRecovery(recovery, PayoutRecordType.갚을_돈_추가_취소,
				orderItemId, null, -amount));
			recovery.cancelByRaceCancel();
		}
		return reverted;
	}

	/**
	 * 최종 정산 내역에서 남은 1차 정산금을 빼고, 아직 못 받은 갚을 돈이 있으면 바로 상계한다 (PRO-214)
	 * 정산 내역을 저장한 뒤에 부른다. 상계가 이 정산 내역을 잠가서 읽기 때문이다
	 * 저장한 정산 내역 엔티티를 받으므로 부른 쪽 트랜잭션 안에서만 돈다(MANDATORY).
	 * 새 트랜잭션을 열면 받은 엔티티가 영속성 컨텍스트 밖이라 차감 줄이 소리 없이 빠질 수 있다
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void deductAdvanceAndSettle(Payout payout, LocalDateTime now) {
		// 남은 1차 정산금 = 1차 정산금 − 회수 요청액. 회수 요청 때 이미 1차 정산금에서 뺐으므로 진행 중인 회수액을 또 빼지 않는다
		// 차감 줄을 쓰기 전에 읽는다. 쓴 뒤에 읽으면 자동 flush로 그 줄이 더해져 0이 된다
		long advance = balance(payout.getSellerId(), payout.getRaceId(), PayoutAccount.선금);
		payout.deductAdvance(advance);
		settle(payout.getSellerId(), payout.getRaceId(), now);
	}

	/**
	 * 정산 대사가 지급 전 최종 정산 내역에 빠진 참가권을 더한 뒤 부른다 (BUK-67). 늘어난 금액에서 남은 갚을 돈을 뺀다
	 * 최종 정산을 만들 때(deductAdvanceAndSettle)와 같은 상계다. 1차 정산금 차감은 만들 때 그때 잔고 전부를 뺐으므로 다시 하지 않는다
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void settleReceivable(Payout payout, LocalDateTime now) {
		settle(payout.getSellerId(), payout.getRaceId(), now);
	}

	// JPQL 합계 쿼리 전에 Hibernate가 쌓인 원장 줄을 먼저 내보내므로(자동 flush) 같은 트랜잭션에서 쓴 줄도 더해진다
	// 같은 패키지의 시나리오 테스트가 잔고를 확인하려고 부르므로 package-private으로 둔다
	long balance(long sellerId, long raceId, PayoutAccount account) {
		return payoutItemRepository.sumBalance(sellerId, raceId, account);
	}

	// 상계: 갚을 돈을 지급 전 정산 내역에서 빼고, 다 갚았으면 그 대회의 요청 중인 회수를 상계로 완료한다
	// 지급 전 정산 내역을 잠가서 읽으므로 지급 배치 · 다른 회수 요청과 정산 내역 금액을 동시에 바꾸지 않는다
	// 다시 불러도 잔고로 다시 계산하므로 이미 뺀 금액을 또 빼지 않는다
	// 부르는 쪽(회수 요청 · 최종 정산 차감)의 트랜잭션 안에서 돈다. 이번에 완료한 회수를 돌려준다
	private List<PayoutRecovery> settle(long sellerId, long raceId, LocalDateTime now) {
		long receivable = balance(sellerId, raceId, PayoutAccount.갚을_돈);
		if (receivable <= 0) {
			return List.of();
		}
		for (Payout payout : payoutRepository.findForUpdate(sellerId, raceId, PayoutStatus.BEFORE_PAYMENT)) {
			receivable -= payout.offsetReceivable(receivable);
			if (receivable == 0) {
				break;
			}
		}
		return completeRequestedIfCleared(sellerId, raceId, now);
	}

	// 그 대회의 갚을 돈이 0이면 요청 중인 회수는 상계로 갚은 몫이므로 상계로 완료한다 (상계 · 입금이 함께 쓰는 규칙)
	// 남은 금액을 넘겨받지 않고 잔고를 다시 읽는다. 방금 쓴 상계 · 입금 줄도 자동 flush로 더해진다
	// 상계와 입금이 같은 잔고를 동시에 읽어 갚을 돈이 0 아래가 됐어도, 더 받을 돈이 없으므로 완료한다
	private List<PayoutRecovery> completeRequestedIfCleared(long sellerId, long raceId, LocalDateTime now) {
		if (balance(sellerId, raceId, PayoutAccount.갚을_돈) > 0) {
			return List.of();
		}
		List<PayoutRecovery> requested = payoutRecoveryRepository.findBySellerIdAndRaceIdAndStatus(
			sellerId, raceId, PayoutRecoveryStatus.회수_요청);
		requested.forEach(recovery -> recovery.completeByOffset(now));
		return requested;
	}

	// 회수를 저장하고, 1차 정산금에서 빼서 같은 금액을 갚을 돈으로 옮긴다
	private PayoutRecovery openRecovery(PayoutRecovery recovery, Long orderItemId, Long raceCourseId) {
		PayoutRecovery saved = payoutRecoveryRepository.save(recovery);
		long amount = saved.getAmount();
		payoutItemRepository.save(
			PayoutItem.ofRecovery(saved, PayoutRecordType.회수_요청, orderItemId, raceCourseId, -amount));
		payoutItemRepository.save(
			PayoutItem.ofRecovery(saved, PayoutRecordType.갚을_돈_추가, orderItemId, raceCourseId, amount));
		return saved;
	}

	// 대회 취소로 닫히지 않은 그 단계의 정산 내역
	private Optional<Payout> findLive(long sellerId, long raceId, PayoutStage stage) {
		return payoutRepository.findFirstBySellerIdAndRaceIdAndPayoutStageOrderByIdAsc(sellerId, raceId, stage)
			.filter(payout -> payout.getStatus() != PayoutStatus.취소);
	}
}
