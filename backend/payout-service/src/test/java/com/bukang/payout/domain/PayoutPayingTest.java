package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.payout.domain.exception.PayoutHoldConflictException;

/**
 * 지급 중 상태: 캐시 서비스에 정산금 이동을 요청하기 전에 커밋해, 결과를 모르는 동안 다른 변경을 막는다 (PRO-252)
 */
class PayoutPayingTest {
	private static final BusinessDayCalendar CALENDAR = new BusinessDayCalendar();
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 4, 0);

	private Payout newPayout() {
		return Payout.finalStage(1, 10, "테스트 대회", LocalDate.of(2026, 10, 17),
			LocalDateTime.of(2026, 10, 18, 3, 0), CALENDAR);
	}

	@Test
	@DisplayName("정산 예정에서 지급을 시작하면 지급 중이 되고 상태 이력이 남는다")
	void startPaying() {
		Payout payout = newPayout();

		payout.startPaying(NOW);

		assertThat(payout.getStatus()).isEqualTo(PayoutStatus.지급_중);
		PayoutStatusHistory last = payout.getStatusHistories().getLast();
		assertThat(last.getFromStatus()).isEqualTo(PayoutStatus.정산_예정);
		assertThat(last.getToStatus()).isEqualTo(PayoutStatus.지급_중);
	}

	@Test
	@DisplayName("지급 중에서 지급 완료로 바꿀 수 있다")
	void payFromPaying() {
		Payout payout = newPayout();
		payout.startPaying(NOW);

		payout.pay(NOW);

		assertThat(payout.getStatus()).isEqualTo(PayoutStatus.지급_완료);
		assertThat(payout.getPaidAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("거절된 지급은 정산 예정으로 되돌리고 사유를 이력에 남긴다. 지급 예정일은 그대로다")
	void revertPaying() {
		Payout payout = newPayout();
		LocalDate scheduledPayDate = payout.getScheduledPayDate();
		payout.startPaying(NOW);

		payout.revertPaying(NOW, "캐시 서비스 거절");

		assertThat(payout.getStatus()).isEqualTo(PayoutStatus.정산_예정);
		assertThat(payout.getScheduledPayDate()).isEqualTo(scheduledPayDate);
		assertThat(payout.getStatusHistories().getLast().getReason()).isEqualTo("캐시 서비스 거절");
	}

	@Test
	@DisplayName("정산 예정이 아니면 지급을 시작할 수 없다 (보류 · 지급 중 · 지급 완료)")
	void cannotStartUnlessCalculated() {
		Payout paying = newPayout();
		paying.startPaying(NOW);
		Payout held = newPayout();
		held.hold(PayoutHoldReason.정산_계좌_미등록, null, 99, NOW);

		assertThatThrownBy(() -> paying.startPaying(NOW)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> held.startPaying(NOW)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("지급 중이 아니면 되돌릴 수 없다")
	void cannotRevertUnlessPaying() {
		Payout payout = newPayout();

		assertThatThrownBy(() -> payout.revertPaying(NOW, "캐시 서비스 거절"))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("지급 중에는 보류를 걸 수 없다 (돈이 이미 나갔을 수 있다)")
	void cannotHoldPaying() {
		Payout payout = newPayout();
		payout.startPaying(NOW);

		assertThatThrownBy(() -> payout.hold(PayoutHoldReason.정산_계좌_미등록, null, 99, NOW))
			.isInstanceOf(PayoutHoldConflictException.class)
			.hasMessage("지급 중인 정산 내역입니다.");
		assertThat(payout.getStatus()).isEqualTo(PayoutStatus.지급_중);
	}

	@Test
	@DisplayName("지급 중에는 대회 취소로 닫을 수 없다")
	void cannotCancelPaying() {
		Payout payout = newPayout();
		payout.startPaying(NOW);

		assertThatThrownBy(() -> payout.cancel(99, NOW, "대회 취소")).isInstanceOf(IllegalStateException.class);
		assertThat(payout.getStatus()).isEqualTo(PayoutStatus.지급_중);
	}

	@Test
	@DisplayName("지급 중에는 회수 상계로 지급액을 바꿀 수 없다 (다시 요청할 때 금액이 처음과 같다)")
	void cannotOffsetPaying() {
		Payout payout = newPayout();
		payout.addItem(100, 50_000L, new BigDecimal("0.0550"), LocalDate.of(2026, 9, 26));
		payout.startPaying(NOW);

		assertThatThrownBy(() -> payout.offsetReceivable(10_000L)).isInstanceOf(IllegalStateException.class);
		assertThat(payout.getPayoutAmount()).isEqualTo(47_250L);
	}

	@Test
	@DisplayName("대금이 상계로 0원이어도 수수료가 남아 있으면 옮길 돈이 있다. 둘 다 0원이면 없다")
	void hasTransferAmount() {
		// 판매액 50,000 − 수수료 2,750 − 1차 정산금 차감 47,250 = 지급액 0원, 수수료 2,750
		Payout feeOnly = Payout.finalStage(1, 10, "테스트 대회", LocalDate.of(2026, 10, 17),
			LocalDateTime.of(2026, 10, 18, 3, 0), CALENDAR);
		feeOnly.deductAdvance(47_250L);
		feeOnly.addItem(100, 50_000L, new BigDecimal("0.0550"), LocalDate.of(2026, 9, 26));
		// 1차 정산금 줄이 없는 1차 정산: 지급액 0원, 수수료 0원
		Payout empty = Payout.first(1, 10, "테스트 대회", LocalDate.of(2026, 9, 30), NOW, CALENDAR);

		assertThat(feeOnly.getPayoutAmount()).isZero();
		assertThat(feeOnly.hasTransferAmount()).isTrue();
		assertThat(empty.hasTransferAmount()).isFalse();
	}
}
