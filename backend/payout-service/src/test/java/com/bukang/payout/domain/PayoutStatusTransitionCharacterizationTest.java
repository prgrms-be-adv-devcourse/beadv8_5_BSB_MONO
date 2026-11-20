package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.bukang.payout.domain.exception.PayoutHoldConflictException;

/**
 * CRU-11 리팩터링 전 상태 전이 검사 동작을 못박는 특성 테스트.
 * hold()의 상태별 거부 문구(switch로 접을 지점), 상태 검사 예외 문구(requireStatus로 모을 지점),
 * 결과 모름 되돌리기(resetUnknownPay), 원장 줄 중복 검사(hasLine)를 고정한다.
 * 모두 메모리 위 도메인 연산이라 Spring 없이 돈다.
 */
class PayoutStatusTransitionCharacterizationTest {
	private static final BusinessDayCalendar CALENDAR = new BusinessDayCalendar();
	private static final LocalDate FEE_DATE = LocalDate.of(2026, 9, 26);
	private static final BigDecimal FEE_RATE = new BigDecimal("0.0550");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 4, 0);

	private Payout calculated() {
		return Payout.finalStage(1, 10, "테스트 대회", LocalDate.of(2026, 10, 17),
			LocalDateTime.of(2026, 10, 18, 3, 0), CALENDAR);
	}

	private Payout paying() {
		Payout payout = calculated();
		payout.startPaying(NOW);
		return payout;
	}

	private Payout paid() {
		Payout payout = calculated();
		payout.pay(NOW);
		return payout;
	}

	private Payout canceled() {
		Payout payout = calculated();
		payout.cancel(99, NOW, "대회 취소");
		return payout;
	}

	private Payout mismatched() {
		Payout payout = paying();
		payout.markPayMismatch(NOW, "409 불일치");
		return payout;
	}

	// 서로 다른 기준일 3번이면 지급_확인_필요로 멈춘다
	private Payout payCheckRequired() {
		Payout payout = paying();
		payout.recordUnknownPay(LocalDate.of(2026, 10, 20), NOW);
		payout.recordUnknownPay(LocalDate.of(2026, 10, 21), NOW);
		boolean stopped = payout.recordUnknownPay(LocalDate.of(2026, 10, 22), NOW);
		assertThat(stopped).isTrue();
		assertThat(payout.getStatus()).isEqualTo(PayoutStatus.지급_확인_필요);
		return payout;
	}

	private PayoutHold hold(Payout payout) {
		return payout.hold(PayoutHoldReason.정산_계좌_미등록, "계좌 확인 중", 99, NOW);
	}

	@Nested
	@DisplayName("보류 걸기: 지급 전만 허용, 그 밖은 상태별 문구로 거부")
	class Hold {
		@Test
		@DisplayName("정산 예정이면 지급보류로 바뀐다")
		void allowsCalculated() {
			Payout payout = calculated();

			hold(payout);

			assertThat(payout.getStatus()).isEqualTo(PayoutStatus.지급보류);
		}

		@Test
		@DisplayName("이미 지급보류면 다른 사유는 더 걸리고 상태는 그대로다")
		void allowsHeldWithAnotherReason() {
			Payout payout = calculated();
			hold(payout);

			payout.hold(PayoutHoldReason.대사_불일치, null, PayoutHold.SYSTEM_MEMBER_ID, NOW);

			assertThat(payout.getStatus()).isEqualTo(PayoutStatus.지급보류);
		}

		@Test
		@DisplayName("지급 중이면 '지급 중인 정산 내역입니다.'")
		void rejectsPaying() {
			assertThatThrownBy(() -> hold(paying()))
				.isInstanceOf(PayoutHoldConflictException.class)
				.hasMessage("지급 중인 정산 내역입니다.");
		}

		@Test
		@DisplayName("지급 완료면 '이미 지급 완료된 정산 내역입니다.'")
		void rejectsPaid() {
			assertThatThrownBy(() -> hold(paid()))
				.isInstanceOf(PayoutHoldConflictException.class)
				.hasMessage("이미 지급 완료된 정산 내역입니다.");
		}

		@Test
		@DisplayName("지급 불일치면 '지급 금액이 맞지 않아 확인 중인 정산 내역입니다.'")
		void rejectsMismatch() {
			assertThatThrownBy(() -> hold(mismatched()))
				.isInstanceOf(PayoutHoldConflictException.class)
				.hasMessage("지급 금액이 맞지 않아 확인 중인 정산 내역입니다.");
		}

		@Test
		@DisplayName("지급 확인 필요면 '지급 결과를 확인 중인 정산 내역입니다.'")
		void rejectsPayCheckRequired() {
			assertThatThrownBy(() -> hold(payCheckRequired()))
				.isInstanceOf(PayoutHoldConflictException.class)
				.hasMessage("지급 결과를 확인 중인 정산 내역입니다.");
		}

		@Test
		@DisplayName("취소면 '취소된 정산 내역입니다.'")
		void rejectsCanceled() {
			assertThatThrownBy(() -> hold(canceled()))
				.isInstanceOf(PayoutHoldConflictException.class)
				.hasMessage("취소된 정산 내역입니다.");
		}
	}

	@Nested
	@DisplayName("상태 검사 예외 문구: 기대 상태가 아니면 그 동작 문구 + status로 막는다")
	class RequireStatus {
		@Test
		@DisplayName("지급 시작은 정산 예정만")
		void startPaying() {
			assertThatThrownBy(() -> paying().startPaying(NOW))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("정산 예정 상태가 아니라 지급을 시작할 수 없습니다")
				.hasMessageContaining("status=지급_중");
		}

		@Test
		@DisplayName("되돌리기는 지급 중만")
		void revertPaying() {
			assertThatThrownBy(() -> calculated().revertPaying(NOW, "거절"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("지급 중 상태가 아니라 되돌릴 수 없습니다")
				.hasMessageContaining("status=지급_예정");
		}

		@Test
		@DisplayName("결과 모름 세기는 지급 중만")
		void recordUnknownPay() {
			assertThatThrownBy(() -> calculated().recordUnknownPay(LocalDate.of(2026, 10, 20), NOW))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("지급 중 상태가 아니라 결과 모름을 셀 수 없습니다")
				.hasMessageContaining("status=지급_예정");
		}

		@Test
		@DisplayName("지급 불일치 표시는 지급 중만")
		void markPayMismatch() {
			assertThatThrownBy(() -> calculated().markPayMismatch(NOW, "409"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("지급 중 상태가 아니라 지급 불일치로 바꿀 수 없습니다")
				.hasMessageContaining("status=지급_예정");
		}

		@Test
		@DisplayName("지급 완료는 정산 예정·지급 중만")
		void pay() {
			assertThatThrownBy(() -> canceled().pay(NOW))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("정산 예정 · 지급 중 상태가 아니라 지급할 수 없습니다")
				.hasMessageContaining("status=취소");
		}

		@Test
		@DisplayName("취소는 지급 전만")
		void cancel() {
			assertThatThrownBy(() -> paid().cancel(99, NOW, "대회 취소"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("지급 전 정산 내역만 취소할 수 있습니다")
				.hasMessageContaining("status=지급_완료");
		}

		@Test
		@DisplayName("대회 취소 표시는 지급 결과를 기다리는 상태만")
		void markRaceCanceled() {
			assertThatThrownBy(() -> calculated().markRaceCanceled(NOW))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("지급 결과를 기다리는 정산 내역만 대회 취소를 표시합니다")
				.hasMessageContaining("status=지급_예정");
		}

		@Test
		@DisplayName("지급 결과를 기다리는 상태(지급 중·지급 확인 필요)면 대회 취소를 표시한다")
		void markRaceCanceledAllowed() {
			assertThat(paying().markRaceCanceled(NOW)).isTrue();
			assertThat(payCheckRequired().markRaceCanceled(NOW)).isTrue();
		}
	}

	@Nested
	@DisplayName("결과 모름 되돌리기: 되돌리면 횟수와 기준일이 초기화된다")
	class ResetUnknownPay {
		@Test
		@DisplayName("거절로 되돌리면 결과 모름 횟수가 0이 된다")
		void revertResets() {
			Payout payout = paying();
			payout.recordUnknownPay(LocalDate.of(2026, 10, 20), NOW);
			assertThat(payout.getUnknownPayCount()).isEqualTo(1);

			payout.revertPaying(NOW, "거절");

			assertThat(payout.getUnknownPayCount()).isZero();
			assertThat(payout.getLastUnknownPayDate()).isNull();
		}

		@Test
		@DisplayName("지급 확인 필요에서 안 나갔다고 확인하면 정산 예정으로 돌리고 결과 모름 횟수를 0으로")
		void confirmNotPaidResets() {
			Payout payout = payCheckRequired();
			assertThat(payout.getUnknownPayCount()).isEqualTo(Payout.MAX_UNKNOWN_PAY);

			payout.confirmNotPaid(99, NOW, "미지급 확인");

			assertThat(payout.getStatus()).isEqualTo(PayoutStatus.지급_예정);
			assertThat(payout.getUnknownPayCount()).isZero();
			assertThat(payout.getLastUnknownPayDate()).isNull();
		}
	}

	@Nested
	@DisplayName("원장 줄 중복 검사: 같은 줄을 두 번 쓰면 막는다")
	class HasLine {
		@Test
		@DisplayName("같은 코스 선금 줄을 두 번 쓰면 막는다")
		void advanceTwicePerCourse() {
			Payout first = Payout.first(1, 10, "테스트 대회", LocalDate.of(2026, 10, 31),
				LocalDateTime.of(2026, 10, 18, 3, 0), CALENDAR);
			first.recordAdvance(7, 50_000L, 1, FEE_DATE);

			assertThatThrownBy(() -> first.recordAdvance(7, 50_000L, 1, FEE_DATE))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("선금 줄은 코스마다 하나만 씁니다");
		}

		@Test
		@DisplayName("다른 코스 선금 줄은 또 쓸 수 있다")
		void advanceAnotherCourse() {
			Payout first = Payout.first(1, 10, "테스트 대회", LocalDate.of(2026, 10, 31),
				LocalDateTime.of(2026, 10, 18, 3, 0), CALENDAR);
			first.recordAdvance(7, 50_000L, 1, FEE_DATE);

			first.recordAdvance(8, 30_000L, 1, FEE_DATE);

			assertThat(first.getItems()).hasSize(2);
		}

		@Test
		@DisplayName("1차 정산금 차감 줄을 두 번 쓰면 막는다")
		void deductTwice() {
			Payout payout = calculated();
			payout.deductAdvance(10_000L);

			assertThatThrownBy(() -> payout.deductAdvance(5_000L))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("1차 정산금 차감 줄은 하나만 씁니다");
		}
	}
}
