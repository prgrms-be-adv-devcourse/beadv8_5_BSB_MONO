package com.bukang.payout.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutAccount;
import com.bukang.payout.domain.PayoutItem;
import com.bukang.payout.domain.PayoutRecordType;

import jakarta.persistence.EntityManager;

@DataJpaTest
@ActiveProfiles("test")
class PayoutRepositoryTest {
	private static final BigDecimal FEE_RATE = new BigDecimal("0.0550");
	private static final BusinessDayCalendar CALENDAR = new BusinessDayCalendar();

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutItemRepository payoutItemRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	@DisplayName("정산 내역(Payout)을 정산 항목(PayoutItem)과 함께 저장하면, 다시 읽을 때 항목도 함께 나온다")
	void saveWithItems() {
		Payout payout = Payout.finalStage(1, 10, "테스트 대회", LocalDate.of(2026, 9, 25),
			LocalDateTime.of(2026, 9, 26, 3, 0),
			CALENDAR);
		payout.addItem(100, 50_000L, FEE_RATE, LocalDate.of(2026, 9, 26));
		payout.addItem(101, 50_000L, FEE_RATE, LocalDate.of(2026, 9, 26));

		payoutRepository.save(payout);
		entityManager.flush();
		entityManager.clear();

		Payout found = payoutRepository.findById(payout.getId()).orElseThrow();
		assertThat(found.getPayoutMonth()).isEqualTo("2026-09");
		assertThat(found.getItems())
			.filteredOn(item -> item.getRecordType() == PayoutRecordType.판매)
			.extracting(PayoutItem::getOrderItemId)
			.containsExactlyInAnyOrder(100L, 101L);
	}

	@Test
	@DisplayName("같은 판매자 · 대회 · 월의 정산 내역은 두 번 저장할 수 없다 (중복 지급 방지)")
	void rejectDuplicateSellerRaceMonth() {
		payoutRepository.saveAndFlush(
			Payout.finalStage(1, 10, "테스트 대회", LocalDate.of(2026, 9, 25),
				LocalDateTime.of(2026, 9, 26, 3, 0), CALENDAR));

		Payout duplicate = Payout.finalStage(1, 10, "테스트 대회", LocalDate.of(2026, 9, 25),
			LocalDateTime.of(2026, 9, 27, 3, 0),
			CALENDAR);

		assertThatThrownBy(() -> payoutRepository.saveAndFlush(duplicate))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("정산 줄을 JDBC로 일괄 저장하면 같은 트랜잭션의 조회와 원장 잔고에 모두 보인다 (BUK-252, 1000줄 넘게)")
	void insertItemsInBatch() {
		Payout payout = Payout.finalStage(1, 10, "테스트 대회", LocalDate.of(2026, 9, 25),
			LocalDateTime.of(2026, 9, 26, 3, 0), CALENDAR);
		List<PayoutItem> lines = new ArrayList<>();
		for (long orderItemId = 1; orderItemId <= 1_001; orderItemId++) {
			lines.addAll(payout.addItemForBatchInsert(orderItemId, 7L, 10_010L, FEE_RATE, LocalDate.of(2026, 9, 26)));
		}
		payoutRepository.save(payout);

		payoutRepository.insertItems(lines);

		assertThat(payoutItemRepository.sumBalance(1, 10, PayoutAccount.번_돈))
			.isEqualTo(payout.getPayoutAmount());
		entityManager.flush();
		entityManager.clear();
		Payout found = payoutRepository.findById(payout.getId()).orElseThrow();
		assertThat(found.getItems()).hasSize(2_002)
			.allSatisfy(item -> {
				assertThat(item.getSellerId()).isEqualTo(1L);
				assertThat(item.getRaceCourseId()).isEqualTo(7L);
				assertThat(item.getFeeRate()).isEqualByComparingTo(FEE_RATE);
				assertThat(item.getPayoutCreateDate()).isEqualTo(LocalDate.of(2026, 9, 26));
				assertThat(item.getBundleKey()).isNull();
				assertThat(item.getCreateDate()).isNotNull();
			});
		assertThat(found.getItems().stream().mapToLong(PayoutItem::getAmount).sum())
			.isEqualTo(found.getPayoutAmount())
			.isEqualTo(1_001 * (10_010L - 550L));
	}
}
