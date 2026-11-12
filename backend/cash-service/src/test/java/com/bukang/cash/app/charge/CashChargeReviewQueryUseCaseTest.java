package com.bukang.cash.app.charge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.dto.PageDto;
import com.bukang.cash.dto.charge.ChargeReviewDto;
import com.bukang.cash.out.charge.WalletChargeRepository;
import com.bukang.cash.out.member.CashMemberRepository;
import com.bukang.cash.out.wallet.WalletRepository;

// 관리자 「고액 충전 확인」 목록 (정책 4장 2차 2-1 「신규 회원 고액 충전 표시」)
// 충전의 요청 시각(createDate)은 저장할 때의 지금이라, 가입 시각을 지금 기준으로 앞뒤로 옮겨 24시간 창 안팎을 만든다
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CashChargeReviewQueryUseCaseTest {
	private static final LocalDateTime NOW = LocalDateTime.now();

	@Autowired
	private CashChargeReviewQueryUseCase cashChargeReviewQueryUseCase;
	@Autowired
	private CashMemberRepository cashMemberRepository;
	@Autowired
	private WalletRepository walletRepository;
	@Autowired
	private WalletChargeRepository walletChargeRepository;

	@Test
	@DisplayName("표시된 충전이 없으면 빈 목록과 전체 건수 0을 돌려준다")
	void empty() {
		Wallet wallet = wallet(21, "러너21", NOW.minusHours(1));
		completedCharge(wallet, 300_000, "order_1", NOW.minusMinutes(5), false);

		PageDto<ChargeReviewDto> result = cashChargeReviewQueryUseCase.findFlagged(0, 20);

		assertThat(result.getContent()).isEmpty();
		assertThat(result.getTotalElements()).isZero();
	}

	@Test
	@DisplayName("표시된 충전을 완료 시각 최신순으로, 회원 정보·표시된 충전·가입 24시간 안 완료 충전 합계와 함께 돌려준다")
	void flaggedChargesWithJoinWindowTotal() {
		LocalDateTime joinedRecently = NOW.minusHours(1);
		Wallet recent = wallet(21, "러너21", joinedRecently);
		completedCharge(recent, 300_000, "order_1", NOW.minusMinutes(20), false);
		completedCharge(recent, 300_000, "order_2", NOW.minusMinutes(10), true);
		// 완료되지 않은 충전은 합계에 넣지 않는다
		WalletCharge failed = new WalletCharge(recent, 100_000, "TOSS", "order_3");
		failed.markFailed();
		walletChargeRepository.save(failed);

		// 가입 24시간이 지난 뒤의 충전은 합계에 넣지 않는다 (다른 회원의 창이 겹쳐 읽혀도 회원마다 따로 거른다)
		Wallet old = wallet(22, "러너22", NOW.minusHours(30));
		completedCharge(old, 200_000, "order_4", NOW.minusMinutes(5), true);

		PageDto<ChargeReviewDto> result = cashChargeReviewQueryUseCase.findFlagged(0, 20);

		assertThat(result.getTotalElements()).isEqualTo(2);
		assertThat(result.getContent()).extracting(ChargeReviewDto::memberId).containsExactly(22L, 21L);
		ChargeReviewDto row = result.getContent().get(1);
		assertThat(row.nickname()).isEqualTo("러너21");
		assertThat(row.joinedAt()).isEqualTo(joinedRecently);
		assertThat(row.chargeAmount()).isEqualTo(300_000);
		assertThat(row.chargedAt()).isEqualTo(NOW.minusMinutes(10));
		assertThat(row.joinWindowChargeTotal()).isEqualTo(600_000);
		assertThat(result.getContent().get(0).joinWindowChargeTotal()).isZero();
	}

	@Test
	@DisplayName("페이지 번호·크기가 범위 밖이면 거절하지 않고 가까운 값(0, 1~100)으로 맞춘다")
	void clampsPage() {
		completedCharge(wallet(21, "러너21", NOW.minusHours(1)), 300_000, "order_1", NOW.minusMinutes(10), true);
		completedCharge(wallet(22, "러너22", NOW.minusHours(1)), 300_000, "order_2", NOW.minusMinutes(5), true);

		PageDto<ChargeReviewDto> tooSmall = cashChargeReviewQueryUseCase.findFlagged(-1, 0);
		PageDto<ChargeReviewDto> tooLarge = cashChargeReviewQueryUseCase.findFlagged(0, 500);

		assertThat(tooSmall.getPage()).isZero();
		assertThat(tooSmall.getSize()).isEqualTo(1);
		assertThat(tooSmall.getContent()).extracting(ChargeReviewDto::memberId).containsExactly(22L);
		assertThat(tooSmall.getTotalPages()).isEqualTo(2);
		assertThat(tooLarge.getSize()).isEqualTo(CashChargeReviewQueryUseCase.MAX_PAGE_SIZE);
	}

	private Wallet wallet(long memberId, String nickname, LocalDateTime joinedAt) {
		CashMember member = cashMemberRepository.save(new CashMember(memberId, joinedAt, joinedAt, nickname));
		return walletRepository.save(new Wallet(member));
	}

	private void completedCharge(Wallet wallet, long amount, String orderId, LocalDateTime chargedAt, boolean flagged) {
		WalletCharge charge = new WalletCharge(wallet, amount, "TOSS", orderId);
		charge.markCompleted("toss_" + orderId, chargedAt, chargedAt);
		if (flagged) {
			charge.markReviewFlagged();
		}
		walletChargeRepository.save(charge);
	}
}
