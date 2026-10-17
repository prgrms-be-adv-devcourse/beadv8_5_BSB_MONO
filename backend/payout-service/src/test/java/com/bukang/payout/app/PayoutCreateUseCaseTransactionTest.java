package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntFunction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutItem;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.PayoutStatusHistoryRepository;
import com.bukang.payout.out.RacePort;
import com.bukang.payout.out.SellerAccountPort;
import com.bukang.payout.out.SoldEntry;
import com.bukang.payout.out.SoldEntryPort;
import com.bukang.payout.support.StubRacePort;

/**
 * 정산 대상일 배치가 판매자 × 대회 정산 내역마다 트랜잭션을 나누는지 확인한다 (PRO-60)
 * 테스트 전체를 감싸는 트랜잭션이 있으면 정산 내역별 커밋 · 롤백이 보이지 않으므로 끄고, 진짜 프록시(PayoutGroupCreator 빈)를 쓴다
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({PayoutGroupCreator.class, BusinessDayCalendar.class, PayoutLedgerUseCase.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PayoutCreateUseCaseTransactionTest {
	private static final LocalDate RACE_DATE = LocalDate.of(2026, 9, 25);
	private static final LocalDate TARGET_DATE = LocalDate.of(2026, 9, 26);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 26, 3, 0);

	// 대회 30의 이름은 PAYOUT_PAYOUT.race_name(200자)보다 길어 정산 내역 저장이 DB에서 실패한다
	private static final IntFunction<Optional<String>> RACE_NAMES = raceId -> raceId == 30
		? Optional.of("가".repeat(201))
		: Optional.of("대회 " + raceId);

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutItemRepository payoutItemRepository;

	@Autowired
	private PayoutHoldRepository payoutHoldRepository;

	@Autowired
	private PayoutGroupCreator payoutGroupCreator;

	@Autowired
	private PayoutStatusHistoryRepository payoutStatusHistoryRepository;

	// deleteAllInBatch는 SQL을 바로 보내 cascade가 돌지 않으므로, 정산 내역을 가리키는 자식 테이블부터 지운다
	@AfterEach
	void cleanUp() {
		payoutHoldRepository.deleteAllInBatch();
		payoutItemRepository.deleteAllInBatch();
		payoutStatusHistoryRepository.deleteAllInBatch();
		payoutRepository.deleteAllInBatch();
	}

	private PayoutCreateUseCase useCaseSelling(List<SoldEntry> entries, SellerAccountPort sellerAccountPort) {
		SoldEntryPort soldEntryPort = (from, to) -> entries;
		return new PayoutCreateUseCase(payoutRepository, payoutGroupCreator, soldEntryPort, sellerAccountPort,
			new StubRacePort(RACE_NAMES, List.of(), entries), raceId -> List.of());
	}

	@Test
	@DisplayName("정산 내역 하나의 저장이 DB에서 실패해도 같은 날 다른 정산 내역은 저장된다")
	void failedGroupDoesNotRollBackOthers() {
		PayoutCreateUseCase useCase = useCaseSelling(List.of(
			new SoldEntry(100, 1, 10, 10, RACE_DATE, 50_000L),
			new SoldEntry(300, 3, 30, 30, RACE_DATE, 40_000L),
			new SoldEntry(200, 2, 20, 20, RACE_DATE, 30_000L)
		), sellerId -> true);

		useCase.run(TARGET_DATE, NOW);

		assertThat(payoutRepository.findAll())
			.extracting(Payout::getSellerId)
			.containsExactlyInAnyOrder(1, 2);
		// 최종 정산 내역 2개 × (1차 정산금 차감 1줄 + 참가권 판매 · 수수료 2줄) (PRO-214)
		assertThat(payoutItemRepository.count()).isEqualTo(6);
	}

	@Test
	@DisplayName("판매자 정보를 묻다 실패한 정산 내역은 건너뛰고, 다른 정산 내역은 저장된다")
	void failedLookupSkipsOnlyThatGroup() {
		// 판매자 2의 계좌 확인이 실패한다 (회원 서비스 장애 등)
		SellerAccountPort sellerAccountPort = sellerId -> {
			if (sellerId == 2) {
				throw new IllegalStateException("회원 서비스 응답 없음");
			}
			return true;
		};
		PayoutCreateUseCase useCase = useCaseSelling(List.of(
			new SoldEntry(100, 1, 10, 10, RACE_DATE, 50_000L),
			new SoldEntry(200, 2, 20, 20, RACE_DATE, 30_000L)
		), sellerAccountPort);

		useCase.run(TARGET_DATE, NOW);

		assertThat(payoutRepository.findAll())
			.extracting(Payout::getSellerId)
			.containsExactly(1);
		assertThat(payoutHoldRepository.count()).isZero();
	}

	@Test
	@DisplayName("어제 실패한 정산 내역은 오늘 실행이 되돌아보기로 다시 만들고, 정산 대상일과 정산 월은 원래 날짜로 남긴다")
	void failedGroupIsCreatedOnNextRun() {
		// 9/26에는 회원 서비스가 멈춰 판매자 2의 계좌 확인이 실패하고, 9/27에는 돌아온다
		AtomicBoolean memberServiceDown = new AtomicBoolean(true);
		SellerAccountPort sellerAccountPort = sellerId -> {
			if (sellerId == 2 && memberServiceDown.get()) {
				throw new IllegalStateException("회원 서비스 응답 없음");
			}
			return true;
		};
		PayoutCreateUseCase useCase = useCaseSelling(List.of(
			new SoldEntry(100, 1, 10, 10, RACE_DATE, 50_000L),
			new SoldEntry(200, 2, 20, 20, RACE_DATE, 30_000L)
		), sellerAccountPort);

		useCase.run(TARGET_DATE, NOW);
		memberServiceDown.set(false);
		useCase.run(TARGET_DATE.plusDays(1), NOW.plusDays(1));

		List<Payout> payouts = payoutRepository.findAll();
		assertThat(payouts)
			.extracting(Payout::getSellerId)
			.containsExactlyInAnyOrder(1, 2);
		Payout retried = payouts.stream().filter(p -> p.getSellerId() == 2).findFirst().orElseThrow();
		assertThat(retried.getPayoutMonth()).isEqualTo("2026-09");
		assertThat(payoutItemRepository.findAll())
			.filteredOn(item -> Integer.valueOf(200).equals(item.getOrderItemId()))
			.extracting(PayoutItem::getTargetDate)
			.containsOnly(TARGET_DATE);
	}
}
