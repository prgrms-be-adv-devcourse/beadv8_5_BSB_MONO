package com.bukang.payout.app;

import org.springframework.stereotype.Service;

import com.bukang.payout.domain.PayoutCreateFailure;
import com.bukang.payout.out.PayoutCreateFailureRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 정산 생성 실패를 실패 기록표에 남기는 기록 전용 유스케이스 (BUK-60)
 * 저장은 새 트랜잭션으로 한다(PayoutCreateFailureRepository.save, 마무리 7번). 바깥 트랜잭션이 생겨 롤백돼도 기록은 남는다
 * 기록이 실패해도 배치는 다음 정산 내역을 만들어야 하므로 예외를 밖으로 던지지 않고 ERROR 로그만 남긴다 (AC-60-5)
 * 이 메서드에 트랜잭션을 걸면 저장 실패가 그 트랜잭션을 롤백 전용으로 만들어, 여기서 잡아도 커밋할 때
 * UnexpectedRollbackException이 밖으로 나간다. 그래서 새 트랜잭션은 저장 메서드에서 열고 이 메서드는 그 바깥에서 잡는다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutCreateFailureUseCase {
	private final PayoutCreateFailureRepository payoutCreateFailureRepository;

	public void record(PayoutCreateFailure failure) {
		try {
			payoutCreateFailureRepository.save(failure);
		} catch (RuntimeException exception) {
			log.error("정산 생성 실패를 기록표에 남기지 못했습니다. stage={}, sellerId={}, raceId={}, 정산 대상일={}, 실패 종류={}, 비고={}",
				failure.getPayoutStage(), failure.getSellerId(), failure.getRaceId(), failure.getPayoutTargetDate(),
				failure.getFailureType(), failure.getNote(), exception);
		}
	}
}
