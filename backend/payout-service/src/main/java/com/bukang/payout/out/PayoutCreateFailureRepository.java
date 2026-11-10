package com.bukang.payout.out;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.PayoutCreateFailure;
import com.bukang.payout.domain.PayoutCreateFailureType;

public interface PayoutCreateFailureRepository extends JpaRepository<PayoutCreateFailure, Long> {
	// 실패 기록은 바깥 트랜잭션과 따로 커밋한다. 이유는 PayoutCreateFailureUseCase에 적었다 (BUK-60)
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	<S extends PayoutCreateFailure> S save(S entity);

	// 관리자 이상 감지 목록 (BUK-108). 기간은 실행일 기준, 종류가 null이면 전체. 최근 기록(id 큰 것)부터
	@Query("""
		select f from PayoutCreateFailure f
		where f.runDate between :from and :to and (:type is null or f.failureType = :type)
		order by f.id desc""")
	Page<PayoutCreateFailure> findAdminRecords(LocalDate from, LocalDate to, PayoutCreateFailureType type,
		Pageable pageable);

	long countByFailureTypeAndRunDateBetween(PayoutCreateFailureType failureType, LocalDate from, LocalDate to);

	// 카드 「자동으로 채운 참가권」: 비고가 '빠진 N장 추가'인 대사 불일치 줄 수 (11/9 사용자 결정, 장수가 아니라 건수)
	// 비고 문구는 PayoutReconcileStepUseCase가 만든다. 문구를 바꾸면 여기도 바꾼다
	@Query("""
		select count(f) from PayoutCreateFailure f
		where f.failureType = com.bukang.payout.domain.PayoutCreateFailureType.대사_불일치
			and f.runDate between :from and :to and f.note like '빠진 %장 추가%'""")
	long countAutoFilled(LocalDate from, LocalDate to);
}
