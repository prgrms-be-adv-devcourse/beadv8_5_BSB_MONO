package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.bukang.payout.domain.exception.PayoutClaimConflictException;

/**
 * 청구 규칙. 기대값의 근거(SPEC = crew-run docs-local/features/2026-10-30-refund-first-recovery.md)
 * - 청구는 갚을 돈이 남은 판매자 × 대회에만 만든다 (SPEC R3, 회수액 0원은 해당 없음 145-146줄)
 * - 관리자 알림: 상계 실패일을 1일째로 세어 30일째에 한 번 (SPEC 130줄 AC-6), 2월을 넘는 경우 (SPEC 149줄)
 * - 같은 날 배치가 두 번 돌아도 청구 알림은 1건 (SPEC 155줄)
 * - 입금 확인은 청구 단위로 한 번 (SPEC R4, docs/policy/06-판매자정산.md:245 "이미 끝낸 청구면 건너뛰어요")
 * - 청구 ID 형식: RC-만든 날-16진수 4자리 (docs/erd/cash.md:288 예 RC-20261214-0A3F)
 * 입금 기한(getDueDate)과 다음 날 청구 알림은 정책이 어긋나거나 정해지지 않아 테스트하지 않는다 (작업 기록 참고)
 */
class PayoutClaimTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 1, 4, 0);

	private static PayoutClaim claimStartingAt(LocalDate startDate) {
		return new PayoutClaim(1, 7, 105_500, startDate);
	}

	@ParameterizedTest(name = "청구액 {0}원은 만들 수 없다")
	@ValueSource(longs = {0, -1})
	@DisplayName("갚을 돈이 남지 않았으면(0원 이하) 청구를 만들지 않는다")
	void rejectsNonPositiveAmount(long amount) {
		assertThatThrownBy(() -> new PayoutClaim(1, 7, amount, LocalDate.of(2026, 11, 1)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("갚을 돈이 1원이라도 남으면 청구 중 상태로 만든다")
	void createsWithOneWon() {
		PayoutClaim claim = new PayoutClaim(1, 7, 1, LocalDate.of(2026, 11, 1));

		assertThat(claim.getAmount()).isEqualTo(1);
		assertThat(claim.getStatus()).isEqualTo(PayoutClaimStatus.청구_중);
	}

	@ParameterizedTest(name = "상계 실패일 {0}: {1}에는 알리지 않고 {2}(30일째)에 알린다")
	@DisplayName("관리자 알림은 상계 실패일을 1일째로 세어 30일째에 남긴다")
	@CsvSource({
		"2026-11-01, 2026-11-29, 2026-11-30",
		// 2월을 넘는 경우 (SPEC 149줄): 평년은 3월 1일, 윤년은 2월 29일
		"2027-01-31, 2027-02-28, 2027-03-01",
		"2028-01-31, 2028-02-28, 2028-02-29"
	})
	void adminNoticeOnThirtiethDay(LocalDate startDate, LocalDate dayBefore, LocalDate thirtiethDay) {
		PayoutClaim claim = claimStartingAt(startDate);

		assertThat(claim.markAdminNotice(dayBefore, NOW)).isFalse();
		assertThat(claim.getAdminNotifiedAt()).isNull();
		assertThat(claim.markAdminNotice(thirtiethDay, NOW)).isTrue();
		assertThat(claim.getAdminNotifiedAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("관리자 알림은 한 번만 남긴다")
	void adminNoticeOnlyOnce() {
		PayoutClaim claim = claimStartingAt(LocalDate.of(2026, 11, 1));
		claim.markAdminNotice(LocalDate.of(2026, 11, 30), NOW);

		assertThat(claim.markAdminNotice(LocalDate.of(2026, 11, 30), NOW.plusHours(1))).isFalse();
		assertThat(claim.getAdminNotifiedAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("같은 날 배치가 두 번 돌아도 판매자 청구 알림은 한 번만 남긴다")
	void sellerNoticeOncePerDay() {
		PayoutClaim claim = claimStartingAt(LocalDate.of(2026, 11, 1));
		LocalDate today = LocalDate.of(2026, 11, 2);

		assertThat(claim.markSellerNotice(today)).isTrue();
		assertThat(claim.markSellerNotice(today)).isFalse();
		assertThat(claim.getSellerNotifiedDate()).isEqualTo(today);
	}

	@Test
	@DisplayName("입금을 확인하면 확인한 사람 · 시각을 남기고, 이미 확인한 청구는 다시 확인할 수 없다")
	void confirmOnlyOnce() {
		PayoutClaim claim = claimStartingAt(LocalDate.of(2026, 11, 1));

		claim.confirm(99, NOW);

		assertThat(claim.getStatus()).isEqualTo(PayoutClaimStatus.입금_확인);
		assertThat(claim.getConfirmedBy()).isEqualTo(99);
		assertThat(claim.getConfirmedAt()).isEqualTo(NOW);
		assertThatThrownBy(() -> claim.confirm(98, NOW.plusDays(1)))
			.isInstanceOf(PayoutClaimConflictException.class);
		assertThat(claim.getConfirmedBy()).isEqualTo(99);
	}

	@ParameterizedTest(name = "ID {0} → {1}")
	@DisplayName("청구 ID는 RC-만든 날-16진수 4자리다")
	@CsvSource({
		// docs/erd/cash.md:288의 예
		"2623, RC-20261214-0A3F",
		// 4자리를 0으로 채운다
		"1, RC-20261214-0001",
		"65535, RC-20261214-FFFF"
	})
	void claimCodeFormat(long id, String expected) {
		PayoutClaim claim = claimStartingAt(LocalDate.of(2026, 12, 13));
		ReflectionTestUtils.setField(claim, "id", id);

		claim.assignClaimCode(LocalDate.of(2026, 12, 14));

		assertThat(claim.getClaimCode()).isEqualTo(expected);
	}
}
