package com.bukang.cash.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.cash.dto.RefundableChargeDto;

// 기대값은 정책 5장 2차 1 「PG 결제취소와 기한」의 2027-10-10 예시에서 가져온다
class WithdrawalPlanTest {
	private RefundableChargeDto charge(long chargeId, long amount, long remaining, boolean expired) {
		return new RefundableChargeDto(
			chargeId, LocalDateTime.of(2026, 10, 10, 9, 0), amount, remaining, LocalDate.of(2027, 10, 9), expired);
	}

	@Test
	@DisplayName("기한 안인 충전은 건별 카드 몫, 기한 지난 몫은 계좌 몫 하나로 나누고, 남은 금액이 0원인 충전은 뺀다")
	void splitsIntoCardPartsAndOneAccountPart() {
		List<RefundableChargeDto> charges = List.of(
			charge(4, 40_000, 40_000, false),
			charge(3, 20_000, 20_000, false),
			charge(2, 10_000, 5_000, true),
			charge(1, 15_000, 0, true));

		WithdrawalPlan plan = WithdrawalPlan.from(charges);

		assertThat(plan.cardParts())
			.extracting(WithdrawalPlan.CardPart::chargeId, WithdrawalPlan.CardPart::amount)
			.containsExactly(tuple(4L, 40_000L), tuple(3L, 20_000L));
		assertThat(plan.accountAmount()).isEqualTo(5_000);
	}

	@Test
	@DisplayName("계좌로 보낼 몫이 있을 때만 계좌 정보가 필요하다 (기한 안 충전만 있으면 필요 없다)")
	void accountRequiredOnlyWhenAccountPartExists() {
		WithdrawalPlan cardOnly = WithdrawalPlan.from(List.of(charge(3, 20_000, 20_000, false)));
		WithdrawalPlan withAccount = WithdrawalPlan.from(List.of(
			charge(3, 20_000, 20_000, false),
			charge(2, 10_000, 5_000, true)));

		assertThat(cardOnly.accountRequired()).isFalse();
		assertThat(withAccount.accountRequired()).isTrue();
	}
}
