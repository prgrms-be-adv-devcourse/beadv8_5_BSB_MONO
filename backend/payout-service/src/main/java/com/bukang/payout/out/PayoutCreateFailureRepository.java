package com.bukang.payout.out;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.PayoutCreateFailure;

public interface PayoutCreateFailureRepository extends JpaRepository<PayoutCreateFailure, Long> {
	// 실패 기록은 바깥 트랜잭션과 따로 커밋한다. 이유는 PayoutCreateFailureUseCase에 적었다 (BUK-60)
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	<S extends PayoutCreateFailure> S save(S entity);
}
