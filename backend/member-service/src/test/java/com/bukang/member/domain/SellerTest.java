package com.bukang.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.member.domain.exception.SellerNotReviewedException;

// 신청 결과 확인 시각 (Seller.checkResult)
// 승인·반려는 이벤트 발행 설정이 필요해, 심사 상태는 칸을 직접 바꿔 만든다
class SellerTest {
	private static final LocalDateTime FIRST = LocalDateTime.of(2026, 10, 21, 10, 0);
	private static final LocalDateTime SECOND = LocalDateTime.of(2026, 10, 21, 11, 0);

	@Test
	@DisplayName("심사가 끝난 신청은 처음 확인한 시각을 남긴다")
	void checkResultRecordsFirstTime() {
		Seller seller = reviewedSeller(SellerStatus.APPROVED);

		seller.checkResult(FIRST);

		assertThat(seller.getResultCheckedAt()).isEqualTo(FIRST);
	}

	@Test
	@DisplayName("두 번째 확인은 처음 시각을 그대로 둔다")
	void checkResultKeepsFirstTime() {
		Seller seller = reviewedSeller(SellerStatus.REJECTED);
		seller.checkResult(FIRST);

		seller.checkResult(SECOND);

		assertThat(seller.getResultCheckedAt()).isEqualTo(FIRST);
	}

	@Test
	@DisplayName("심사 중인 신청은 기록하지 않고 예외를 던진다")
	void checkResultFailsWhilePending() {
		Seller seller = newSeller();

		assertThatThrownBy(() -> seller.checkResult(FIRST))
			.isInstanceOf(SellerNotReviewedException.class)
			.hasMessage("아직 심사 중인 판매자 신청입니다.");
		assertThat(seller.getResultCheckedAt()).isNull();
	}

	private Seller reviewedSeller(SellerStatus status) {
		Seller seller = newSeller();
		ReflectionTestUtils.setField(seller, "status", status);
		return seller;
	}

	private Seller newSeller() {
		return new Seller(null, SellerType.INDIVIDUAL, null, null, "김러너",
			new VerifiedPayoutAccount("004", "1101230000678", "김러너", PayoutAccountHolderType.PERSONAL, FIRST),
			FIRST.minusDays(1));
	}
}
