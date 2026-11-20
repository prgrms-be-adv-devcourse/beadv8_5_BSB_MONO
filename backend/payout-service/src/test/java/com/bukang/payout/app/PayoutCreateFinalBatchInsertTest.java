package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.payout.app.PayoutCreateStepUseCase.FinalPayoutSource;
import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutItem;
import com.bukang.payout.domain.PayoutRecordType;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutItemBatchInsert;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.PayoutStatusHistoryRepository;
import com.bukang.payout.out.SoldEntry;

/**
 * 최종 정산의 참가권 줄을 JDBC로 1000줄씩 일괄 저장해도(BUK-252) 정산 내역 · 줄 · 보류가 한 트랜잭션으로 묶이는지 본다
 * 롤백을 보려면 테스트 전체를 감싸는 트랜잭션이 없어야 하므로 끄고, 생성 단계는 TransactionTemplate으로 감싼다
 */
@DataJpaTest
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PayoutCreateFinalBatchInsertTest {
	private static final long SELLER_ID = 1;
	private static final long RACE_ID = 7;
	private static final long PRICE = 10_010L;
	// 1장의 수수료: 10,010 × 5.5% = 550.55 → 550
	private static final long FEE = 550L;
	private static final LocalDate RACE_DATE = LocalDate.of(2026, 12, 13);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 14, 3, 0);
	// 참가권마다 두 줄이므로 600장이면 1,200줄이다. 첫 1000줄 묶음이 DB에 들어간 뒤의 실패를 보려고 1000줄을 넘긴다
	private static final int ENTRY_COUNT = 600;

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutItemRepository payoutItemRepository;

	@Autowired
	private PayoutHoldRepository payoutHoldRepository;

	@Autowired
	private PayoutRecoveryRepository payoutRecoveryRepository;

	@Autowired
	private PayoutStatusHistoryRepository payoutStatusHistoryRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	// deleteAllInBatch는 cascade가 돌지 않으므로 정산 내역을 가리키는 표부터 지운다
	@AfterEach
	void cleanUp() {
		payoutItemRepository.deleteAllInBatch();
		payoutHoldRepository.deleteAllInBatch();
		payoutStatusHistoryRepository.deleteAllInBatch();
		payoutRepository.deleteAllInBatch();
	}

	private void createFinal(List<SoldEntry> entries) {
		BusinessDayCalendar calendar = new BusinessDayCalendar();
		PayoutCreateStepUseCase step = new PayoutCreateStepUseCase(payoutRepository, calendar,
			new PayoutLedgerUseCase(payoutRepository, payoutItemRepository, payoutRecoveryRepository),
			PayoutHoldUseCases.withoutNotification(payoutRepository, payoutHoldRepository, calendar));
		// 계좌 미등록이라 자동 보류가 함께 생긴다
		FinalPayoutSource source = new FinalPayoutSource(SELLER_ID, RACE_ID, "대회 A", false, entries);
		new TransactionTemplate(transactionManager).executeWithoutResult(
			status -> step.createFinal(source, RACE_DATE, NOW));
	}

	private static List<SoldEntry> entries(long fromOrderItemId, long toOrderItemId) {
		return new ArrayList<>(LongStream.rangeClosed(fromOrderItemId, toOrderItemId)
			.mapToObj(id -> new SoldEntry(id, SELLER_ID, RACE_ID, RACE_ID, RACE_DATE, PRICE))
			.toList());
	}

	@Test
	@DisplayName("참가권 600장(1,200줄)을 일괄 저장하면 정산 내역 금액이 저장된 줄의 합과 같다")
	void storedLinesSumToPayoutAmount() {
		createFinal(entries(1, ENTRY_COUNT));

		Payout payout = payoutRepository.findAll().getFirst();
		List<PayoutItem> lines = payoutItemRepository.findAll();
		assertThat(lines).filteredOn(line -> line.getRecordType() == PayoutRecordType.판매).hasSize(ENTRY_COUNT);
		assertThat(lines).filteredOn(line -> line.getRecordType() == PayoutRecordType.수수료).hasSize(ENTRY_COUNT);
		assertThat(payout.getSalesAmount()).isEqualTo(ENTRY_COUNT * PRICE);
		assertThat(payout.getFeeAmount()).isEqualTo(ENTRY_COUNT * FEE);
		assertThat(payout.getPayoutAmount())
			.isEqualTo(ENTRY_COUNT * (PRICE - FEE))
			.isEqualTo(lines.stream().mapToLong(PayoutItem::getAmount).sum());
		assertThat(payoutHoldRepository.count()).isOne();
	}

	@Test
	@DisplayName("줄이 묶음 크기(1000줄)에 딱 맞아도 빠지거나 두 번 들어가는 줄이 없다")
	void linesExactlyOneBatch() {
		int entryCount = PayoutItemBatchInsert.BATCH_SIZE / 2;
		createFinal(entries(1, entryCount));

		Payout payout = payoutRepository.findAll().getFirst();
		// 참가권 줄 1000개 + 선지급 차감 줄 1개(0원)
		assertThat(payoutItemRepository.findAll())
			.filteredOn(line -> line.getOrderItemId() != null)
			.hasSize(PayoutItemBatchInsert.BATCH_SIZE);
		assertThat(payout.getSalesAmount()).isEqualTo(entryCount * PRICE);
	}

	@Test
	@DisplayName("첫 1000줄을 넣은 뒤 줄 저장이 실패하면 정산 내역 · 이미 넣은 줄 · 보류가 모두 롤백된다")
	void failureAfterFirstBatchRollsBackEverything() {
		List<SoldEntry> entries = entries(1, ENTRY_COUNT);
		// 마지막 참가권이 첫 참가권과 같아 1,199번째 줄에서 (참가권, 줄 종류) 유일 제약에 걸린다
		entries.set(ENTRY_COUNT - 1, entries.getFirst());

		assertThatThrownBy(() -> createFinal(entries)).isInstanceOf(DataIntegrityViolationException.class);

		assertThat(payoutRepository.count()).isZero();
		assertThat(payoutItemRepository.count()).isZero();
		assertThat(payoutHoldRepository.count()).isZero();
	}
}
