package com.bukang.payout.app;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.domain.exception.PayoutNotFoundException;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.PayoutTransfer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 지급 배치의 정산 내역 한 건 단계마다 짧은 트랜잭션을 연다 (PRO-252)
 * 캐시 서비스 호출(HTTP)은 이 트랜잭션들 밖에서 한다. 호출하는 동안 정산 DB 연결과 행 잠금을 잡지 않기 위해서다
 * 지급 중(PAYING)을 호출 전에 커밋해 두므로, 트랜잭션 밖에 있어도 그동안 보류 · 상계 · 취소가 끼어들지 못한다
 * PayoutPayUseCase가 부른다. 같은 클래스 안에서 부르면 @Transactional 프록시를 거치지 않으므로 빈을 나눈다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutPayStepUseCase {
	private final PayoutRepository payoutRepository;

	@Transactional(readOnly = true)
	public List<Long> findTargetIds(LocalDate today) {
		return payoutRepository.findPayTargetIds(today, PayoutStatus.CALCULATED, PayoutStatus.PAYING);
	}

	/**
	 * 지급을 시작한다. 대상을 고른 뒤 상태가 바뀌었을 수 있으므로 다시 읽어 판단한다
	 * - 지급 예정일이 된 정산 예정: 옮길 돈이 있으면 지급 중으로 바꿔 커밋하고, 없으면(대금 · 수수료 0원) 바로 지급 완료한다
	 * - 지급 중: 지난 실행에서 결과를 모른 건이다. 상태를 그대로 두고 같은 금액으로 다시 요청한다
	 *   (지급 중에는 상계가 지급액을 바꾸지 못하므로 처음 요청과 금액이 같다)
	 * - 최종인데 같은 판매자 · 대회의 1차가 아직 지급 완료가 아니면 건너뛴다. 정산 예정으로 두고 다음 실행이 다시 본다 (PRO-188)
	 * - 그 밖(보류 · 지급 완료 · 취소, 아직 지급 예정일 전): 건너뛴다
	 * 다른 배치가 같은 정산 내역을 먼저 지급 중으로 커밋했으면, 커밋할 때 낙관적 락 예외가 난다. 부르는 쪽이 건너뛴다
	 */
	@Transactional
	public Start start(long payoutId, LocalDate today, LocalDateTime now) {
		Payout payout = payoutRepository.findById(payoutId).orElseThrow(PayoutNotFoundException::new);
		if (payout.getStatus() == PayoutStatus.PAYING) {
			return Start.transfer(PayoutTransfer.from(payout));
		}
		if (payout.getStatus() != PayoutStatus.CALCULATED || payout.getScheduledPayDate().isAfter(today)) {
			return Start.skipped();
		}
		Optional<Payout> waitingFirst = findWaitingFirst(payout);
		if (waitingFirst.isPresent()) {
			// 보류가 안 풀린 1차 때문에 최종이 계속 밀리면 원인을 찾을 수 있게 실행마다 한 줄 남긴다
			log.info("1차 정산이 지급 완료되지 않아 최종 정산 지급을 미룹니다. payoutId={}, firstPayoutId={}, firstStatus={}",
				payout.getId(), waitingFirst.get().getId(), waitingFirst.get().getStatus());
			return Start.skipped();
		}
		if (!payout.hasTransferAmount()) {
			payout.pay(now);
			return Start.paidWithoutTransfer();
		}
		payout.startPaying(now);
		return Start.transfer(PayoutTransfer.from(payout));
	}

	// 최종은 1차가 지급 완료된 뒤에만 지급한다 (PRO-188, 10/28 결정). 1차가 보류 · 지급 중이면 최종도 기다린다
	// 살아 있는 1차(대회 취소로 닫히지 않은 1차, PayoutLedgerUseCase.findLive와 같은 기준)가 없으면 기다리지 않는다.
	// 1차를 만들지 못한 채 최종이 만들어진 경우이고, 그 뒤 1차는 만들어지지 않는다 (PRO-190)
	// 지급 중(PAYING)인 최종의 재시도에는 걸지 않는다. 이미 돈이 나갔을 수 있어 결과를 확인해야 한다
	// 같은 날 1차 · 최종이 함께 대상이면 같은 실행에서 1차 → 최종 순서로 지급한다(같은 날 두 번 이체해도 된다, 10/28 결정).
	// 지급 배치가 ID 순서로 처리하고 1차가 최종보다 먼저 저장되기 때문이다 (PayoutRepository.findPayTargetIds)
	// 최종이 기다려야 하는 1차를 돌려준다. 기다리지 않아도 되면 빈 값
	private Optional<Payout> findWaitingFirst(Payout payout) {
		if (payout.getPayoutStage() != PayoutStage.FINAL) {
			return Optional.empty();
		}
		return payoutRepository.findFirstBySellerIdAndRaceIdAndPayoutStageOrderByIdAsc(
				payout.getSellerId(), payout.getRaceId(), PayoutStage.FIRST)
			.filter(first -> first.getStatus() != PayoutStatus.CANCELED)
			.filter(first -> first.getStatus() != PayoutStatus.PAID);
	}

	// 캐시 서비스가 돈을 옮긴 뒤 지급 완료로 바꾼다
	// 다른 배치가 같은 지급 중 건을 다시 요청해 먼저 지급 완료했으면 바꾸지 않는다 (캐시 서비스는 돈을 한 번만 옮겼다)
	@Transactional
	public boolean complete(long payoutId, LocalDateTime now) {
		Payout payout = payoutRepository.findById(payoutId).orElseThrow(PayoutNotFoundException::new);
		if (payout.getStatus() == PayoutStatus.PAID) {
			return false;
		}
		payout.pay(now);
		return true;
	}

	// 캐시 서비스가 거절해 돈이 옮겨지지 않았으므로 정산 예정으로 되돌린다. 다음 실행에서 다시 지급한다
	@Transactional
	public void revert(long payoutId, LocalDateTime now, String reason) {
		Payout payout = payoutRepository.findById(payoutId).orElseThrow(PayoutNotFoundException::new);
		payout.revertPaying(now, reason);
	}

	/**
	 * 지급 시작 결과
	 *
	 * @param outcome  다음에 할 일
	 * @param transfer TRANSFER일 때 캐시 서비스에 보낼 요청. 그 밖에는 null
	 */
	public record Start(Outcome outcome, PayoutTransfer transfer) {
		static Start transfer(PayoutTransfer transfer) {
			return new Start(Outcome.TRANSFER, transfer);
		}

		static Start paidWithoutTransfer() {
			return new Start(Outcome.PAID_WITHOUT_TRANSFER, null);
		}

		static Start skipped() {
			return new Start(Outcome.SKIPPED, null);
		}
	}

	public enum Outcome {
		// 캐시 서비스에 정산금 이동을 요청한다
		TRANSFER,
		// 옮길 돈이 0원이라 요청 없이 지급 완료했다
		PAID_WITHOUT_TRANSFER,
		// 대상을 고른 뒤 상태가 바뀌어 지급하지 않는다
		SKIPPED
	}
}
