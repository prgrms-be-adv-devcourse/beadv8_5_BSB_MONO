package com.bukang.payout.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.AfterTransaction;

import com.bukang.payout.domain.PayoutCreateFailure;
import com.bukang.payout.domain.PayoutCreateFailureAction;
import com.bukang.payout.domain.PayoutCreateFailureType;
import com.bukang.payout.domain.PayoutStage;

import jakarta.persistence.EntityManager;

@DataJpaTest
@ActiveProfiles("test")
class PayoutCreateFailureRepositoryTest {

	@Autowired
	private PayoutCreateFailureRepository payoutCreateFailureRepository;

	// 기록 저장은 새 트랜잭션으로 커밋돼 테스트 롤백으로 지워지지 않는다. 다른 테스트에 남지 않게 지운다
	@AfterTransaction
	void deleteFailures() {
		payoutCreateFailureRepository.deleteAllInBatch();
	}

	@Autowired
	private EntityManager entityManager;

	@Test
	@DisplayName("판매자 × 대회 실패 한 줄을 저장하면 한글 실패 종류와 칸이 그대로 다시 읽힌다")
	void saveSellerRaceFailure() {
		PayoutCreateFailure saved = payoutCreateFailureRepository.save(PayoutCreateFailure.sellerRace(PayoutStage.최종_정산,
			1, 10, LocalDate.of(2026, 11, 5), PayoutCreateFailureType.주문_정산_대회일_불일치, null, LocalDate.of(2026, 11, 10)));
		entityManager.flush();
		entityManager.clear();

		PayoutCreateFailure found = payoutCreateFailureRepository.findById(saved.getId()).orElseThrow();
		assertThat(found.getPayoutStage()).isEqualTo(PayoutStage.최종_정산);
		assertThat(found.getSellerId()).isEqualTo(1L);
		assertThat(found.getRaceId()).isEqualTo(10L);
		assertThat(found.getRaceCourseId()).isNull();
		assertThat(found.getPayoutTargetDate()).isEqualTo(LocalDate.of(2026, 11, 5));
		assertThat(found.getFailureType()).isEqualTo(PayoutCreateFailureType.주문_정산_대회일_불일치);
		assertThat(found.getNote()).isNull();
		assertThat(found.getActionTaken()).isEqualTo(PayoutCreateFailureAction.기록만);
		assertThat(found.getRunDate()).isEqualTo(LocalDate.of(2026, 11, 10));
		assertThat(found.getCreateDate()).isNotNull();
	}

	@Test
	@DisplayName("대회 목록 조회 실패는 판매자 · 대회 · 정산 대상일 칸이 비고, 비고에 본 기간이 남는다")
	void saveRaceLookupFailure() {
		PayoutCreateFailure saved = payoutCreateFailureRepository.save(PayoutCreateFailure.raceLookup(PayoutStage.최종_정산,
			LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10)));
		entityManager.flush();
		entityManager.clear();

		PayoutCreateFailure found = payoutCreateFailureRepository.findById(saved.getId()).orElseThrow();
		assertThat(found.getSellerId()).isNull();
		assertThat(found.getRaceId()).isNull();
		assertThat(found.getPayoutTargetDate()).isNull();
		assertThat(found.getFailureType()).isEqualTo(PayoutCreateFailureType.대회_조회_실패);
		assertThat(found.getNote()).isEqualTo("from~to: 2026/11/3~2026/11/9");
	}

	// 실행일 11/5 · 11/6 · 11/7에 기록이 하나씩 쌓였다
	private void saveThreeDays() {
		payoutCreateFailureRepository.save(PayoutCreateFailure.reconcileMismatch(PayoutStage.최종_정산, 1, 10, null,
			LocalDate.of(2026, 11, 4), "빠진 3장 추가 (코스 3: 3장)", PayoutCreateFailureAction.자동_보정,
			null, null, null, LocalDate.of(2026, 11, 5)));
		payoutCreateFailureRepository.save(PayoutCreateFailure.orderLookup(PayoutStage.일차_정산, 20,
			LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6)));
		payoutCreateFailureRepository.save(PayoutCreateFailure.reconcileMismatch(PayoutStage.최종_정산, 2, 30, 4L,
			LocalDate.of(2026, 11, 6), "더 들어감 2장 (코스 4: 2장)", PayoutCreateFailureAction.지급보류,
			null, null, null, LocalDate.of(2026, 11, 7)));
	}

	@Test
	@DisplayName("관리자 기록 목록은 실행일이 기간 안인 줄을 최근 기록부터 돌려준다 (BUK-108)")
	void findAdminRecordsByRunDate() {
		saveThreeDays();

		Page<PayoutCreateFailure> page = payoutCreateFailureRepository.findAdminRecords(LocalDate.of(2026, 11, 6),
			LocalDate.of(2026, 11, 7), null, PageRequest.of(0, 50));

		assertThat(page.getContent()).extracting(PayoutCreateFailure::getRunDate)
			.containsExactly(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 6));
		assertThat(page.getTotalElements()).isEqualTo(2);
	}

	@Test
	@DisplayName("종류를 고르면 그 종류만 돌려준다")
	void findAdminRecordsByType() {
		saveThreeDays();

		Page<PayoutCreateFailure> page = payoutCreateFailureRepository.findAdminRecords(LocalDate.of(2026, 11, 1),
			LocalDate.of(2026, 11, 30), PayoutCreateFailureType.대사_불일치, PageRequest.of(0, 50));

		assertThat(page.getContent()).extracting(PayoutCreateFailure::getSellerId).containsExactly(2L, 1L);
	}

	@Test
	@DisplayName("카드 숫자: 기간 안의 종류별 건수와, 비고가 '빠진 N장 추가'인 대사 불일치 건수를 센다")
	void countCards() {
		saveThreeDays();
		LocalDate from = LocalDate.of(2026, 11, 1);
		LocalDate to = LocalDate.of(2026, 11, 30);

		assertThat(payoutCreateFailureRepository.countByFailureTypeAndRunDateBetween(
			PayoutCreateFailureType.대사_불일치, from, to)).isEqualTo(2);
		assertThat(payoutCreateFailureRepository.countByFailureTypeAndRunDateBetween(
			PayoutCreateFailureType.주문_조회_실패, from, to)).isEqualTo(1);
		assertThat(payoutCreateFailureRepository.countAutoFilled(from, to)).isEqualTo(1);
		assertThat(payoutCreateFailureRepository.countAutoFilled(LocalDate.of(2026, 11, 6), to)).isZero();
	}
}
