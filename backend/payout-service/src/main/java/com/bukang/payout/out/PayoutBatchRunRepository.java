package com.bukang.payout.out;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.PayoutBatchRun;
import com.bukang.payout.domain.PayoutBatchRunStatus;
import com.bukang.payout.domain.PayoutBatchType;

public interface PayoutBatchRunRepository extends JpaRepository<PayoutBatchRun, Long> {
	// 실행 기록은 배치 트랜잭션과 따로 커밋한다. 배치가 롤백돼도 "돌았다 · 멈췄다"는 남아야 한다 (BUK-167)
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	<S extends PayoutBatchRun> S save(S entity);

	// 서버가 배치 도중 꺼져 진행 중으로 남은 기록 (PR #431 리뷰). 다음 실행이 실패로 닫는다
	List<PayoutBatchRun> findByBatchTypeInAndStatusAndStartedAtBefore(Collection<PayoutBatchType> batchTypes,
		PayoutBatchRunStatus status, LocalDateTime startedAt);

	// 관리자 실행 기록 목록. 최근 실행부터
	List<PayoutBatchRun> findByRunDateBetweenOrderByIdDesc(LocalDate from, LocalDate to);
}
