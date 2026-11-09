package com.bukang.payout.app;

import java.time.LocalDateTime;
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
import com.bukang.payout.domain.PayoutRecoveryCompletion;
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
 * 일정 변경 환불 · 대회 취소의 회수 요청과 관리자의 입금 확인도 여기서 받는다 (payout.md 4-2 · 4-3, PRO-33 · PRO-34)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutLedgerUseCase {
	private final PayoutRepository payoutRepository;
	private final PayoutItemRepository payoutItemRepository;
	private final PayoutRecoveryRepository payoutRecoveryRepository;

	/**
	 * 일정 변경 환불의 회수 요청 (payout.md 4-2, PRO-33 · PRO-214)
	 * 1차 정산을 만든 뒤의 환불은 1차 지급 전 · 뒤를 나누지 않고 항상 회수 요청으로 기록한다
	 * 회수액은 그 참가권의 1차 몫(가격 × 40%, 원 미만 버림)이다. 가격은 주문 쪽 값을 받는다
	 * 요청하면 같은 판매자 × 대회의 지급 전 정산 내역에서 바로 상계하고, 다 갚으면 회수가 바로 끝난다(구매자 환불로 이어짐)
	 *
	 * 지금은 정산 쪽 진입점만 만든다. 주문 서비스가 환불 신청 때 부르는 연결과 "회수 대기" 환불은 order 담당과 맞춘다
	 *
	 * @param raceCourseId 환불된 참가권의 코스 (원장 줄에 적어 코스별로 본다)
	 * @param price 환불된 참가권의 가격 (주문 상품 가격)
	 * @return 만든 회수. 1차 정산이 없으면(1차 정산금을 준 적이 없으면) 돌려받을 것이 없어 빈 값
	 */
	@Transactional
	public Optional<PayoutRecovery> recoverScheduleChange(long sellerId, long raceId, long raceCourseId,
		long orderItemId, long price, LocalDateTime now) {
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
		PayoutRecovery recovery = openRecovery(
			PayoutRecovery.scheduleChanged(sellerId, raceId, orderItemId, amount, now), orderItemId, raceCourseId);
		settle(sellerId, raceId, now);
		return Optional.of(recovery);
	}

	/**
	 * 대회 취소의 회수 요청 (payout.md 4-3, PRO-34 · PRO-214). 대회당 1건만 만든다
	 * 판매자가 손에 든 돈 = 남은 1차 정산금 + 번 돈. 지급 전 정산 내역은 먼저 되돌렸으므로 지급한 정산 내역의 몫만 남는다
	 * 이미 요청 중인 갚을 돈은 따로 받으므로 더하지 않는다
	 *
	 * @return 만든 회수. 이미 있거나 돌려받을 돈이 없으면 빈 값
	 */
	@Transactional
	public Optional<PayoutRecovery> recoverRaceCancel(long sellerId, long raceId, LocalDateTime now) {
		if (payoutRecoveryRepository.existsByRaceIdAndReasonType(raceId, PayoutRecoveryReason.대회_취소)) {
			return Optional.empty();
		}
		long advance = balance(sellerId, raceId, PayoutAccount.선금);
		long earning = balance(sellerId, raceId, PayoutAccount.번_돈);
		if (earning != 0) {
			// 최종 지급 뒤의 대회 취소는 번 돈까지 되돌려야 하는데 아직 정하지 않았다 (PRO-214 미룸). 금액만 맞춰 1차 정산금 줄로 남긴다
			log.warn("최종 지급 뒤 대회 취소: 번 돈까지 회수합니다. sellerId={}, raceId={}, earning={}",
				sellerId, raceId, earning);
		}
		long amount = advance + earning;
		if (amount < 0) {
			// 회수 요청액이 준 1차 정산금보다 많은 상태(지급 전 정산 내역에 상계했던 회수가 남음). 처리 규칙을 정하지 않았다
			log.warn("대회 취소 회수액이 음수라 만들지 않습니다. sellerId={}, raceId={}, amount={}", sellerId, raceId, amount);
		}
		// 지급액이 0원인 정산 내역(0원 지급 완료)만 있으면 돌려받을 돈이 없다
		if (amount <= 0) {
			return Optional.empty();
		}
		return Optional.of(openRecovery(
			new PayoutRecovery(sellerId, raceId, PayoutRecoveryReason.대회_취소, amount, now), null, null));
	}

	/**
	 * 대회 취소로 지급 전 1차 정산 내역을 닫은 뒤, 그 1차 정산 내역에서 상계로 끝났던 일정 변경 회수를 되돌린다 (R9)
	 * 1차 정산 내역의 상계 줄은 정산 내역 취소로 되돌아가지만 회수 줄(요청 · 갚을 돈 추가)은 회수에 붙어 남는다.
	 * 그대로 두면 판매자가 받은 적 없는 몫이 1차 정산금 잔고 −, 갚을 돈 +로 남아 청구된다
	 * 최종 정산이 없을 때만 부른다. 그때 상계로 끝난 회수는 모두 1차 정산 내역에서 갚은 몫이다
	 * 대회 취소 회수(recoverRaceCancel)를 만들기 전에 불러야 남은 1차 정산금을 맞게 센다
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public List<PayoutRecovery> revertOffsetRecoveries(long sellerId, long raceId) {
		List<PayoutRecovery> offset = payoutRecoveryRepository.findBySellerIdAndRaceIdAndStatus(
				sellerId, raceId, PayoutRecoveryStatus.회수_완료).stream()
			.filter(recovery -> recovery.getReasonType() == PayoutRecoveryReason.일정_변경)
			.filter(recovery -> recovery.getCompletion() == PayoutRecoveryCompletion.상계)
			.toList();
		for (PayoutRecovery recovery : offset) {
			long amount = recovery.getAmount();
			Long orderItemId = recovery.getOrderItemId();
			payoutItemRepository.save(PayoutItem.ofRecovery(recovery, PayoutRecordType.회수_요청_취소,
				orderItemId, null, amount));
			payoutItemRepository.save(PayoutItem.ofRecovery(recovery, PayoutRecordType.갚을_돈_추가_취소,
				orderItemId, null, -amount));
			recovery.cancelByRaceCancel();
		}
		return offset;
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
