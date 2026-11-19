package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;

/**
 * CRU-12 리팩터링 전 bundle_key 부여 동작을 못박는 특성 테스트.
 * 참가권 없는 줄은 저장 직전(@PrePersist)에 키를 받고, 상계 줄과 참가권 있는 줄은 받지 않는다.
 * usesBundleKey()로 "키를 쓰는 종류" 판단을 옮기기 전 기준점이다.
 */
@DataJpaTest
@ActiveProfiles("test")
class PayoutBundleKeyCharacterizationTest {
	private static final BusinessDayCalendar CALENDAR = new BusinessDayCalendar();
	private static final LocalDateTime SETTLED_AT = LocalDateTime.of(2026, 11, 1, 3, 0);
	private static final LocalDate CREATE_DATE = LocalDate.of(2026, 11, 1);

	@Autowired
	private EntityManager entityManager;

	private Payout persistPayout() {
		Payout payout = Payout.first(1, 10, "대회 A", LocalDate.of(2026, 10, 31), SETTLED_AT, CALENDAR);
		entityManager.persist(payout);
		entityManager.flush();
		return payout;
	}

	private PayoutRecovery persistRecovery() {
		PayoutRecovery recovery = new PayoutRecovery(1, 10, PayoutRecoveryReason.대회_취소, 20_000, SETTLED_AT);
		entityManager.persist(recovery);
		entityManager.flush();
		return recovery;
	}

	private String persistedKey(PayoutItem item) {
		entityManager.persist(item);
		entityManager.flush();
		return item.getBundleKey();
	}

	@Test
	@DisplayName("정산 내역 선금 줄은 코스를 넣은 P{id}:선금:C{코스} 키를 받는다")
	void advanceLineHasPayoutKeyWithCourse() {
		Payout payout = persistPayout();

		PayoutItem advance = PayoutItem.ofPayout(payout, PayoutRecordType.선금, null, 7L, 20_000L, null, CREATE_DATE);

		assertThat(persistedKey(advance)).isEqualTo("P%d:선금:C7".formatted(payout.getId()));
	}

	@Test
	@DisplayName("코스 없는 선지급 차감 줄은 P{id}:선지급_차감 키를 받는다")
	void deductLineHasPayoutKeyWithoutCourse() {
		Payout payout = persistPayout();

		PayoutItem deduct = PayoutItem.ofPayout(payout, PayoutRecordType.선지급_차감, null, null, -5_000L, null, null);

		assertThat(persistedKey(deduct)).isEqualTo("P%d:선지급_차감".formatted(payout.getId()));
	}

	@Test
	@DisplayName("상계 줄은 키를 받지 않는다 (다시 계산해 0원이면 안 생기므로)")
	void offsetLineHasNoKey() {
		Payout payout = persistPayout();

		PayoutItem offset = PayoutItem.ofPayout(payout, PayoutRecordType.회수_상계, null, null, -3_000L, null, null);

		assertThat(persistedKey(offset)).isNull();
	}

	@Test
	@DisplayName("상계 취소 줄도 키를 받지 않는다")
	void offsetCancelLineHasNoKey() {
		Payout payout = persistPayout();

		PayoutItem offsetCancel = PayoutItem.ofPayout(payout, PayoutRecordType.회수_상계_취소, null, null, 3_000L, null,
			null);

		assertThat(persistedKey(offsetCancel)).isNull();
	}

	@Test
	@DisplayName("참가권 있는 줄(판매)은 키를 받지 않는다 (order_item_id 유일 제약으로 막는다)")
	void participationLineHasNoKey() {
		Payout payout = persistPayout();

		PayoutItem sale = PayoutItem.ofPayout(payout, PayoutRecordType.판매, 101L, 7L, 50_000L, null, CREATE_DATE);

		assertThat(persistedKey(sale)).isNull();
	}

	@Test
	@DisplayName("참가권 없는 회수 줄은 R{id}:종류 키를 받는다 (예전 대회당 1건 회수)")
	void recoveryLineWithoutOrderItemHasRecoveryKey() {
		PayoutRecovery recovery = persistRecovery();

		PayoutItem request = PayoutItem.ofRecovery(recovery, PayoutRecordType.회수_요청, null, null, -20_000L);

		assertThat(persistedKey(request)).isEqualTo("R%d:회수_요청".formatted(recovery.getId()));
	}

	@Test
	@DisplayName("참가권 있는 회수 줄은 키를 받지 않는다 (order_item_id 유일 제약으로 막는다)")
	void recoveryLineWithOrderItemHasNoKey() {
		PayoutRecovery recovery = persistRecovery();

		PayoutItem request = PayoutItem.ofRecovery(recovery, PayoutRecordType.회수_요청, 108L, null, -20_000L);

		assertThat(persistedKey(request)).isNull();
	}

	@Test
	@DisplayName("청구 입금 확인 줄은 C{청구id}:종류 키를 ofClaim에서 바로 받는다")
	void claimLineHasClaimKey() {
		PayoutClaim claim = new PayoutClaim(1, 10, 105_500L, CREATE_DATE);
		entityManager.persist(claim);
		entityManager.flush();

		PayoutItem deposit = PayoutItem.ofClaim(claim, PayoutRecordType.회수_입금, -105_500L);

		assertThat(persistedKey(deposit)).isEqualTo("C%d:회수_입금".formatted(claim.getId()));
	}
}
