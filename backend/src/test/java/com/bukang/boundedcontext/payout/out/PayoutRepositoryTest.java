package com.bukang.boundedcontext.payout.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.boundedcontext.payout.domain.Payout;
import com.bukang.boundedcontext.payout.domain.PayoutItem;
import com.bukang.boundedcontext.payout.domain.PayoutRecordType;
import com.bukang.global.config.crypto.CryptoConfig;

import jakarta.persistence.EntityManager;

// 슬라이스 테스트는 일반 @Configuration을 띄우지 않는다. Member 엔티티의 암호화 컨버터가 쓰는 TextEncryptor 빈을 직접 불러온다
@DataJpaTest
@Import(CryptoConfig.class)
@ActiveProfiles("test")
class PayoutRepositoryTest {
	private static final BigDecimal FEE_RATE = new BigDecimal("0.0550");

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	@DisplayName("정산 내역(Payout)을 정산 항목(PayoutItem)과 함께 저장하면, 다시 읽을 때 항목도 함께 나온다")
	void saveWithItems() {
		Payout payout = new Payout(1, 10, YearMonth.of(2026, 9), LocalDateTime.of(2026, 9, 29, 3, 0));
		payout.addItem(100, PayoutRecordType.SALE, 50_000L, FEE_RATE, LocalDate.of(2026, 9, 29));
		payout.addItem(101, PayoutRecordType.SALE, 50_000L, FEE_RATE, LocalDate.of(2026, 9, 29));

		payoutRepository.save(payout);
		entityManager.flush();
		entityManager.clear();

		Payout found = payoutRepository.findById(payout.getId()).orElseThrow();
		assertThat(found.getPayoutMonth()).isEqualTo("2026-09");
		assertThat(found.getItems())
			.extracting(PayoutItem::getOrderItemId)
			.containsExactlyInAnyOrder(100, 101);
	}

	@Test
	@DisplayName("같은 판매자 · 대회 · 월의 정산 내역은 두 번 저장할 수 없다 (중복 지급 방지)")
	void rejectDuplicateSellerRaceMonth() {
		payoutRepository.saveAndFlush(
			new Payout(1, 10, YearMonth.of(2026, 9), LocalDateTime.of(2026, 9, 29, 3, 0)));

		Payout duplicate = new Payout(1, 10, YearMonth.of(2026, 9), LocalDateTime.of(2026, 9, 30, 3, 0));

		assertThatThrownBy(() -> payoutRepository.saveAndFlush(duplicate))
			.isInstanceOf(DataIntegrityViolationException.class);
	}
}
