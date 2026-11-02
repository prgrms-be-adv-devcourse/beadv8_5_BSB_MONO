package com.bukang.payout.in;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutHold;
import com.bukang.payout.domain.PayoutHoldReason;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.domain.PayoutTargetDate;
import com.bukang.payout.domain.TwoStagePayoutCalculator;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 개발용 판매자 정산 샘플 데이터 (판매자 화면 https://crewrun-alpha.vercel.app/seller 기준)
 * 대회 이름 · 참가비 · 판매 수는 화면에서 가져오고, 금액 · 지급일 · 정산 ID는 도메인 메서드가 2단계 정산 규칙으로 계산한다.
 * 1차는 환불 마감일 다음 날, 최종은 대회 다음 날 03:00에 만들고(정산 생성 배치와 같다), 지급 예정일은
 * 1차 환불 마감일 + 2일 · 최종 대회일 + 2일, 보류를 풀면 max(원래 지급 예정일, 해제일 + 1일)이다 (10/28 결정).
 * 환불 마감일은 상품 서비스를 부르지 않고 대회일 10일 전으로 둔다.
 * 날짜는 처음 만든 날(오늘) 기준의 상대 날짜라, 누가 언제 dev로 띄워도 아래 상태가 모두 보인다 (ProductDataInit과 같은 방식).
 *   지급 완료 1대회(1), 보류를 풀어 지급 완료 1대회(6, 해제된 보류 2건), 1차 지급 완료 · 최종 내일 지급 예정(7),
 *   1차 지급 완료 · 최종 지급보류(8, 지급보류 1건), 1차만 내일 지급 예정(9, 대회 전)
 * 처음 만든 날의 상대 날짜로 정해지므로, 시간이 지나면 지급 예정이 그날 04:00 지급 배치에서 지급 완료로 바뀐다.
 * 처음 상태를 다시 보려면 dev DB에서 판매자 7의 정산 데이터를 지운다.
 * 대회 이름은 화면의 이름을 직접 넣고, 대회 ID는 아래 번호를 쓴다 (상품 서비스 ProductDataInit의 더미 대회와는 연결되지 않는다)
 *   1 2026 봄 한강 10K / 2 2026 용감한 쿠키RUN in 서울 / 3 2026 춘천 나이트런 / 4 2027 부산 바다 10K / 5 2026 가을 트레일런
 *   6 2026 여름 야간 레이스 / 7 2026 한강 나이트런 / 8 2026 가을 하프마라톤 / 9 2026 서울숲 가을 10K
 *   (2 ~ 5는 대회 전이라 정산 대상일이 오지 않아 정산 데이터가 없다)
 */
@Profile("dev")
@Configuration
@Slf4j
public class PayoutDataInit {
	// 판매자1의 판매자 ID. member-service MemberDataInit이 SEED_SELLER_ID(7)로 직접 넣는다
	// 신청으로 만든 판매자는 1000부터 시작하므로(V202610161110__add_member_role_and_seller.sql) 7과 겹치지 않는다
	private static final long SELLER_ID = 7;
	// 관리자의 회원 ID. MemberDataInit의 가입 순서(system 1, holding 2, 관리자 3)에 기댄다
	// dev DB에 다른 회원이 먼저 있었다면 번호가 달라지므로 MEMBER_MEMBER에서 확인한다
	private static final long ADMIN_MEMBER_ID = 3;
	private static final String ZONE = "Asia/Seoul";

	private static final BigDecimal FEE_RATE = TwoStagePayoutCalculator.FEE_RATE;
	private static final int REFUND_DEADLINE_DAYS_BEFORE_RACE = 10;
	private static final long PRICE_10K = 45_000L;
	private static final long PRICE_5K = 35_000L;
	private static final long PRICE_HALF = 60_000L;

	private final PayoutDataInit self;
	private final PayoutRepository payoutRepository;
	private final PayoutHoldRepository payoutHoldRepository;
	private final BusinessDayCalendar calendar;

	public PayoutDataInit(
		@Lazy PayoutDataInit self,
		PayoutRepository payoutRepository,
		PayoutHoldRepository payoutHoldRepository,
		BusinessDayCalendar calendar
	) {
		this.self = self;
		this.payoutRepository = payoutRepository;
		this.payoutHoldRepository = payoutHoldRepository;
		this.calendar = calendar;
	}

	@Bean
	@Order(1)
	public ApplicationRunner payoutDataInitApplicationRunner() {
		return args -> self.makeSamplePayouts(LocalDate.now(ZoneId.of(ZONE)));
	}

	@Transactional
	public void makeSamplePayouts(LocalDate today) {
		// dev DB는 재시작해도 데이터가 남으므로(ddl-auto: update) 이미 만들었으면 건너뛴다
		// 날짜가 오늘 기준이라 정산 월은 띄운 날마다 달라진다. 그래서 판매자 · 대회 · 단계로만 확인한다
		if (payoutRepository.existsBySellerIdAndRaceIdAndPayoutStage(SELLER_ID, 1, PayoutStage.FINAL)) {
			return;
		}

		// 봄 한강 10K (화면 '종료', 2,000 / 2,000): 대회 60일 전 → 1차 · 최종 모두 지급 예정일 04:00에 지급 완료
		StagePair spring = createPayouts(1, "2026 봄 한강 10K", today.minusDays(60), 2_000, PRICE_10K);
		payOnScheduledDate(spring.first());
		payOnScheduledDate(spring.last());

		// 여름 야간 레이스: 판매자가 계좌를 늦게 등록한 경우. 1차 · 최종 모두 대상일에 자동 보류 → 30일 전 관리자 해제
		// → 원래 지급 예정일이 지났으므로 해제일 + 1일에 지급 완료
		StagePair summer = createPayouts(6, "2026 여름 야간 레이스", today.minusDays(45), 800, PRICE_5K);
		for (Payout payout : List.of(summer.first(), summer.last())) {
			PayoutHold accountHold = hold(payout, PayoutHoldReason.ACCOUNT_NOT_REGISTERED, null,
				PayoutHold.SYSTEM_MEMBER_ID, payout.getSettledAt());
			payout.release(accountHold, ADMIN_MEMBER_ID, today.minusDays(30).atTime(10, 30), calendar);
			payOnScheduledDate(payout);
		}

		// 한강 나이트런 (화면 정산 탭): 어제 대회 → 1차는 이미 지급 완료, 최종은 오늘 03:00에 만들어져 내일 지급 예정
		// 요약의 다음 정산일 · 정산 예정액이 된다
		StagePair nightRun = createPayouts(7, "2026 한강 나이트런", today.minusDays(1), 1_200, PRICE_10K);
		payOnScheduledDate(nightRun.first());

		// 가을 하프마라톤: 사흘 전 대회 → 1차는 지급 완료, 최종은 어제 지급 예정이었으나 그 전날 관리자가 보류 (지급보류 1건)
		StagePair half = createPayouts(8, "2026 가을 하프마라톤", today.minusDays(3), 1_500, PRICE_HALF);
		payOnScheduledDate(half.first());
		hold(half.last(), PayoutHoldReason.RACE_CANCELED, "기상 악화로 하프 종목 중단, 환불 범위 확인 중", ADMIN_MEMBER_ID,
			half.last().getSettledAt().plusHours(11));

		// 서울숲 가을 10K: 어제 환불 마감 → 1차가 오늘 03:00에 만들어져 내일 지급 예정. 대회(9일 뒤) 전이라 최종은 없다
		createFirst(9, "2026 서울숲 가을 10K", today.minusDays(1), 600, PRICE_10K);

		log.info("정산 샘플 데이터 생성: sellerId={}, today={}", SELLER_ID, today);
	}

	// 1차 · 최종을 함께 만든다. 최종은 같은 대회 1차의 지급액을 1차 정산금 차감으로 뺀다
	private StagePair createPayouts(long raceId, String raceName, LocalDate raceDate, int count, long price) {
		Payout first = createFirst(raceId, raceName, raceDate.minusDays(REFUND_DEADLINE_DAYS_BEFORE_RACE), count,
			price);

		LocalDate finalTargetDate = PayoutTargetDate.of(raceDate);
		Payout last = Payout.finalStage(SELLER_ID, raceId, raceName, raceDate,
			finalTargetDate.atTime(3, 0), calendar);
		last.deductAdvance(first.getPayoutAmount());
		addItems(last, raceId, count, price, finalTargetDate);
		save(last);
		return new StagePair(first, last);
	}

	// 정산 생성 배치(PayoutCreateUseCase)처럼 환불 마감일 다음 날 03:00에 만든다
	// 대회 하나는 코스 하나라 참가권 가격이 하나다 (PRO-214). 1차는 판매 집계(가격 합 · 장수)로 1차 정산금 한 줄을 쓴다
	private Payout createFirst(long raceId, String raceName, LocalDate refundDeadline, int count, long price) {
		LocalDate firstTargetDate = PayoutTargetDate.firstOf(refundDeadline);
		Payout first = Payout.first(SELLER_ID, raceId, raceName, refundDeadline,
			firstTargetDate.atTime(3, 0), calendar);
		// 샘플은 대회마다 코스가 하나라고 보고 코스 ID를 대회 ID와 같게 둔다
		first.recordAdvance(raceId, price * count, count, firstTargetDate);
		save(first);
		return first;
	}

	// 지급 배치(04:00)가 지급 예정일에 지급한 것처럼 남긴다
	private void payOnScheduledDate(Payout payout) {
		payout.pay(payout.getScheduledPayDate().atTime(4, 0));
	}

	private void addItems(Payout payout, long raceId, int count, long price, LocalDate targetDate) {
		for (int i = 1; i <= count; i++) {
			// 주문 상품 ID는 대회마다 겹치지 않게 대회 번호 × 100,000부터 붙인다
			payout.addItem(raceId * 100_000 + i, raceId, price, FEE_RATE, targetDate);
		}
	}

	private void save(Payout payout) {
		payoutRepository.save(payout);
		payout.assignPayoutCode();
	}

	private PayoutHold hold(Payout payout, PayoutHoldReason reasonType, String reason, long heldBy,
		LocalDateTime heldAt) {
		PayoutHold hold = payout.hold(reasonType, reason, heldBy, heldAt);
		payoutHoldRepository.save(hold);
		hold.assignHoldCode();
		return hold;
	}

	private record StagePair(Payout first, Payout last) {
	}
}
