package com.bukang.payout.app;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.bukang.payout.app.PayoutCreateStepUseCase.CourseSale;
import com.bukang.payout.app.PayoutCreateStepUseCase.FinalPayoutSource;
import com.bukang.payout.app.PayoutCreateStepUseCase.FirstPayoutSource;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.domain.PayoutTargetDate;
import com.bukang.payout.dto.PayoutCreateResultDto;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.RacePort;
import com.bukang.payout.out.RaceSchedule;
import com.bukang.payout.out.SellerAccountPort;
import com.bukang.payout.out.SoldEntry;
import com.bukang.payout.out.SoldEntryPort;
import com.bukang.payout.out.SoldSummary;
import com.bukang.payout.out.SoldSummaryPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 정산 생성 배치: 정산 대상일이 된 대회의 정산 내역(판매자 × 대회 × 월 × 단계)과 정산 항목을 만든다 (payout.md 4-3)
 * 한 실행에서 1차(환불 마감일 다음 날)를 먼저, 최종(대회 다음 날)을 나중에 만든다. 놓친 날을 따라잡을 때
 * 같은 대회의 1차와 최종이 한 실행에 함께 오면, 최종이 1차 지급액을 1차 정산금 차감으로 읽을 수 있어야 하기 때문이다
 * 판매자가 정산 계좌를 등록하지 않았으면 만들 때 '정산 계좌 미등록'으로 보류한다 (payout.md 1장 지급보류 2번, PRO-27)
 * 대상 대회는 상품 서비스에 먼저 묻는다 (1차는 환불 마감일, 최종은 대회일 기준). 대회 이름도 이때 받아 정산 내역에 저장한다
 * 판매 값은 그다음 대회 ID로 주문 서비스에 묻는다 (1차는 판매 집계, 최종은 참가권 목록) (PRO-214)
 *
 * 정산 내역(판매자 × 대회)마다 트랜잭션을 따로 연다 (PRO-60). 하루치를 트랜잭션 하나로 만들면 정산 내역 하나의 실패로
 * 그날 모든 판매자의 정산 내역이 롤백되고, 배치는 지난 날을 다시 보지 않아 다시 만들어지지 않는다
 * 그래서 이 클래스에는 트랜잭션을 걸지 않고, 실패한 정산 내역은 로그를 남기고 다음 정산 내역을 처리한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutCreateUseCase {
	// 되돌아보기 기간: 어제 대상일부터 7일치. 일주일 안의 장애는 저절로 따라잡는다 (PRO-61)
	private static final int LOOKBACK_DAYS = 7;

	private final PayoutRepository payoutRepository;
	private final PayoutCreateStepUseCase payoutCreateStepUseCase;
	private final SoldEntryPort soldEntryPort;
	private final SellerAccountPort sellerAccountPort;
	private final RacePort racePort;
	private final SoldSummaryPort soldSummaryPort;

	/**
	 * today의 배치를 돌린다. 1차 · 최종 모두 어제까지 LOOKBACK_DAYS일 동안 대상일이 된 대회를 보고, 아직 없는 정산 내역만 만든다
	 * 03:00 실행을 놓쳐도(서버 중단, 배포, 실패한 정산 내역) 다음 실행들이 따라잡는다. @Scheduled는 놓친 실행을 다시 돌리지 않는다
	 */
	public PayoutCreateResultDto run(LocalDate today, LocalDateTime now) {
		Counts first = createFirstPayoutsForRaces(today, now);

		LocalDate toRaceDate = PayoutTargetDate.raceDateFor(today);
		LocalDate fromRaceDate = toRaceDate.minusDays(LOOKBACK_DAYS - 1);
		Counts last = createFinalPayoutsForRaces(fromRaceDate, toRaceDate, now);

		log.info("정산 생성 배치 완료. 1차 생성 {} · 이미 있음 {} · 실패 {}, 최종(대회일 {} ~ {}) 생성 {} · 이미 있음 {} · 실패 {}",
			first.created, first.skipped, first.failed, fromRaceDate, toRaceDate, last.created, last.skipped,
			last.failed);
		return new PayoutCreateResultDto(fromRaceDate, toRaceDate, first.created + last.created,
			first.skipped + last.skipped, first.failed + last.failed);
	}

	// 1차: 환불 마감일이 되돌아보기 기간 안인 대회마다 판매 집계(가격 합 · 장수)로 만든다. 정산 월은 환불 마감일의 달이다
	// 참가권 목록은 받지 않는다. 대회 하나는 코스 하나라 가격이 하나여서 집계만으로 1차 정산금을 계산할 수 있다 (PRO-214)
	private Counts createFirstPayoutsForRaces(LocalDate today, LocalDateTime now) {
		LocalDate toRefundDeadline = PayoutTargetDate.refundDeadlineFor(today);
		LocalDate fromRefundDeadline = toRefundDeadline.minusDays(LOOKBACK_DAYS - 1);

		Counts counts = new Counts();
		for (RaceSchedule race : racePort.findRacesByRefundDeadline(fromRefundDeadline, toRefundDeadline)) {
			List<SoldSummary> summaries;
			try {
				summaries = soldSummaryPort.summarizeSold(race.raceId());
			} catch (RuntimeException exception) {
				counts.failRace(PayoutStage.FIRST, race.raceId(), exception);
				continue;
			}
			// 정산 내역은 판매자 × 대회 단위이고, 코스별 집계는 그 안의 1차 정산금 줄이 된다
			summaries.stream()
				.collect(groupingBy(SoldSummary::sellerId))
				.forEach((sellerId, courses) -> createFirstPayout(new SellerRace(sellerId, race.raceId()), courses,
					race.raceName(), race.refundDeadline(), counts, now));
		}
		return counts;
	}

	// 1차 정산 내역 하나를 만든다. 이미 만든 정산 내역은 건너뛰고, 실패하면 이 정산 내역만 실패로 남긴다
	private void createFirstPayout(SellerRace key, List<SoldSummary> courses, String raceName,
		LocalDate refundDeadline, Counts counts, LocalDateTime now) {
		try {
			// 1차는 판매자 × 대회마다 한 번이라 정산 월은 보지 않는다. 판매 시작 뒤에도 환불 마감일을 늦출 수 있어
			// (상품 Race.update) 마감일이 다음 달로 넘어가면 정산 월이 바뀌고, 월까지 보면 같은 대회의 1차가
			// 한 번 더 만들어져 1차 정산금이 두 번 나간다 (PRO-232)
			if (payoutRepository.existsBySellerIdAndRaceIdAndPayoutStage(key.sellerId(), key.raceId(),
				PayoutStage.FIRST)) {
				counts.skipped++;
				return;
			}
			// 최종이 먼저 만들어졌으면 1차를 만들지 않는다. 환불 마감일과 대회일이 되돌아보기 기간(7일) 안에 있으면
			// 1차가 실패한 실행에서 최종이 1차 정산금 차감 0원(판매자 정산액 전부)으로 만들어지고, 다음 실행에서 1차가
			// 만들어져 1차 정산금이 한 번 더 나간다. 최종이 이미 전액을 계산했으므로 건너뛴다 (1차 없는 최종은 PRO-190)
			if (payoutRepository.existsBySellerIdAndRaceIdAndPayoutStage(key.sellerId(), key.raceId(),
				PayoutStage.FINAL)) {
				log.warn("최종 정산이 이미 있어 1차 정산을 만들지 않습니다. sellerId={}, raceId={}", key.sellerId(),
					key.raceId());
				counts.skipped++;
				return;
			}
			List<CourseSale> courseSales = courses.stream()
				.map(course -> new CourseSale(course.raceCourseId(), course.priceSum(), course.ticketCount()))
				.toList();
			payoutCreateStepUseCase.createFirst(new FirstPayoutSource(key.sellerId(), key.raceId(), raceName,
				sellerAccountPort.hasPayoutAccount(key.sellerId()), courseSales), refundDeadline, now);
			counts.created++;
		} catch (RuntimeException exception) {
			if (isSavedByOther(exception, () -> payoutRepository.existsBySellerIdAndRaceIdAndPayoutStage(
				key.sellerId(), key.raceId(), PayoutStage.FIRST))) {
				counts.skipSavedByOther(key, PayoutStage.FIRST);
				return;
			}
			counts.failed++;
			log.error("1차 정산 내역을 만들지 못했습니다. sellerId={}, raceId={}, 코스 {}개",
				key.sellerId(), key.raceId(), courses.size(), exception);
		}
	}

	// 최종: 대회일이 되돌아보기 기간 안인 대회마다 판매 내역(참가권 목록)으로 만든다. 정산 월은 대회일의 달이다
	// 대상 대회는 1차처럼 상품 서비스가 정하고, 판매 내역은 그 대회의 ID로 묻는다. 주문 상품에 복사된 대회일로
	// 기간을 물으면 상품의 대회일과 어긋난 참가권은 대상 대회에서 빠지거나 다른 기간에 잡힌다
	private Counts createFinalPayoutsForRaces(LocalDate fromRaceDate, LocalDate toRaceDate, LocalDateTime now) {
		Counts counts = new Counts();
		for (RaceSchedule race : racePort.findRacesByRaceDate(fromRaceDate, toRaceDate)) {
			List<SoldEntry> entries;
			try {
				entries = soldEntryPort.findSoldEntries(race.raceId());
			} catch (RuntimeException exception) {
				counts.failRace(PayoutStage.FINAL, race.raceId(), exception);
				continue;
			}
			YearMonth payoutMonth = YearMonth.from(race.raceDate());
			entries.stream()
				.collect(groupingBy(SoldEntry::sellerId))
				.forEach((sellerId, sellerEntries) -> createFinalPayout(new SellerRace(sellerId, race.raceId()),
					sellerEntries, race, payoutMonth, counts, now));
		}
		return counts;
	}

	// 최종 정산 내역 하나를 만든다. 이 정산 내역만 실패로 남기고 다음 정산 내역을 계속 만든다
	// 참가권의 대회일이 상품의 대회일과 다르면 만들지 않는다. 실패한 정산 내역과 그 항목 · 보류는 함께 롤백된다
	private void createFinalPayout(SellerRace key, List<SoldEntry> entries, RaceSchedule race, YearMonth payoutMonth,
		Counts counts, LocalDateTime now) {
		try {
			checkRaceDate(key, entries, race);
			// 정산 월은 실행일이 아니라 대회일로 정한다. 실행일로 정하면 월말 대회를 다음 달 실행이
			// 다시 볼 때 정산 월이 달라져 아래 중복 확인을 통과하고, 같은 참가권이 두 번 지급된다
			// 이미 만든 정산 내역은 건너뛴다 (되돌아보기로 매일 같은 대회를 다시 보므로 대부분 여기서 걸린다)
			// 확인과 저장 사이에 다른 인스턴스가 먼저 저장하면 DB 유일 제약이 막고, 이미 있음으로 센다 (PRO-120)
			if (payoutRepository.existsBySellerIdAndRaceIdAndPayoutMonthAndPayoutStage(
				key.sellerId(), key.raceId(), payoutMonth.toString(), PayoutStage.FINAL)) {
				counts.skipped++;
				return;
			}
			payoutCreateStepUseCase.createFinal(new FinalPayoutSource(key.sellerId(), key.raceId(), race.raceName(),
				sellerAccountPort.hasPayoutAccount(key.sellerId()), entries), race.raceDate(), now);
			counts.created++;
		} catch (RuntimeException exception) {
			if (isSavedByOther(exception, () -> payoutRepository.existsBySellerIdAndRaceIdAndPayoutMonthAndPayoutStage(
				key.sellerId(), key.raceId(), payoutMonth.toString(), PayoutStage.FINAL))) {
				counts.skipSavedByOther(key, PayoutStage.FINAL);
				return;
			}
			counts.fail(key, entries, exception);
		}
	}

	// 존재 확인과 저장 사이에 다른 서버가 같은 정산 내역을 먼저 저장하면 DB 유일 제약에 걸린다 (PRO-120)
	// 그 정산 내역은 이미 있으므로 실패가 아니다. 다른 제약 위반(값이 너무 김 등)이면 정산 내역이 없으므로 실패로 남긴다
	// 다시 확인하는 조회도 실패하면(DB 연결 끊김 등) 있는지 모르므로 실패로 센다. 예외를 밖으로 던지면 반복이 끊겨
	// 다음 정산 내역을 만들지 못한다 (PR #252 리뷰)
	private static boolean isSavedByOther(RuntimeException exception, BooleanSupplier savedNow) {
		if (!(exception instanceof DataIntegrityViolationException)) {
			return false;
		}
		try {
			return savedNow.getAsBoolean();
		} catch (RuntimeException recheckException) {
			log.warn("유일 제약 위반 뒤 정산 내역이 있는지 다시 확인하지 못해 실패로 셉니다.", recheckException);
			return false;
		}
	}

	// 참가권에 복사된 대회일이 상품의 대회일과 다르면 주문 쪽 데이터가 어긋난 것이므로
	// 어느 날로 정산할지 고르지 않고 실패로 남긴다 (일정 변경 반영은 PRO-115)
	private void checkRaceDate(SellerRace key, List<SoldEntry> entries, RaceSchedule race) {
		Set<LocalDate> raceDates = entries.stream().map(SoldEntry::raceDate).collect(toSet());
		if (!raceDates.equals(Set.of(race.raceDate()))) {
			throw new IllegalStateException("참가권의 대회일이 상품의 대회일과 다릅니다. raceId=%d, 상품=%s, 참가권=%s"
				.formatted(key.raceId(), race.raceDate(), raceDates));
		}
	}

	// 정산 내역 하나로 묶이는 판매자 × 대회
	private record SellerRace(long sellerId, long raceId) {
	}

	// 단계 하나의 실행 결과
	private static final class Counts {
		private int created;
		private int skipped;
		private int failed;

		// 다른 실행이 먼저 만든 정산 내역은 정상 상황이라 실패로 세지 않고 WARN만 남긴다 (PRO-120)
		private void skipSavedByOther(SellerRace key, PayoutStage stage) {
			skipped++;
			log.warn("다른 실행이 같은 정산 내역을 먼저 만들어 건너뜁니다. stage={}, sellerId={}, raceId={}",
				stage, key.sellerId(), key.raceId());
		}

		private void failRace(PayoutStage stage, long raceId, RuntimeException exception) {
			failed++;
			log.error("정산용 판매 값을 주문 서비스에서 받지 못했습니다. stage={}, raceId={}", stage, raceId, exception);
		}

		private void fail(SellerRace key, List<SoldEntry> entries, RuntimeException exception) {
			failed++;
			log.error("정산 내역을 만들지 못했습니다. sellerId={}, raceId={}, 참가권 {}장",
				key.sellerId(), key.raceId(), entries.size(), exception);
		}
	}
}
