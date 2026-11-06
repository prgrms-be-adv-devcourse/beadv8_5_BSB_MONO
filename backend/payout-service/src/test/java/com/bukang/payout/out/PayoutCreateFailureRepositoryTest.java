package com.bukang.payout.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.AfterTransaction;

import com.bukang.payout.domain.PayoutCreateFailure;
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
}
