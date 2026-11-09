package com.bukang.payout.app;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingLong;
import static java.util.stream.Collectors.toMap;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutCreateDate;
import com.bukang.payout.domain.PayoutCreateFailure;
import com.bukang.payout.domain.PayoutCreateFailureAction;
import com.bukang.payout.domain.PayoutHoldReason;
import com.bukang.payout.domain.PayoutItem;
import com.bukang.payout.domain.PayoutRecordType;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.domain.TicketDiff;
import com.bukang.payout.domain.TicketDiffer;
import com.bukang.payout.domain.TwoStagePayoutCalculator;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.RaceSchedule;
import com.bukang.payout.out.SoldEntry;
import com.bukang.payout.out.SoldSummary;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 정산 대사의 대회 하나(단계 하나)를 트랜잭션 하나로 비교하고 고친다 (BUK-67)
 * 빠진 참가권 추가와 대사 불일치 보류가 함께 커밋되거나 함께 롤백된다. 판매 값은 부르는 쪽이 미리 받아 넘긴다
 * (주문 서비스 응답을 기다리는 동안 DB 트랜잭션을 열어 두지 않기 위해서다)
 * 어긋남은 기록으로 돌려주고, 기록 저장은 부르는 쪽이 이 트랜잭션 밖에서 한다
 * PayoutReconcileUseCase 안의 메서드로 두면 트랜잭션 프록시를 거치지 않으므로 별도 빈으로 둔다 (PayoutCreateStepUseCase와 같은 방식)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutReconcileStepUseCase {
	// 실패 기록표 비고 칸 길이 (payout_payout_create_failure.note varchar(255))
	private static final int NOTE_MAX_LENGTH = 255;

	private final PayoutRepository payoutRepository;
	private final PayoutHoldUseCase payoutHoldUseCase;
	private final PayoutLedgerUseCase payoutLedgerUseCase;
	private final TicketDiffer ticketDiffer;

	/**
	 * 1차: 판매자마다 판매 합(코스별 집계의 합)과 1차 정산 판매액 합을 비교한다 (AC-67-1 ~ 3)
	 * 정산 합이 더 크면 돈이 더 나가므로 보류한다 (AC-67-13). 코스끼리 어긋나 합이 같으면 잡지 못한다 (Q10에서 받아들임)
	 */
	@Transactional
	public List<PayoutCreateFailure> reconcileFirst(RaceSchedule race, List<SoldSummary> summaries, LocalDate today,
		LocalDateTime now) {
		Map<Long, Long> soldBySeller = summaries.stream()
			.collect(groupingBy(SoldSummary::sellerId, summingLong(SoldSummary::priceSum)));
		Map<Long, List<Payout>> payoutsBySeller = livePayouts(race.raceId(), PayoutStage.일차_정산);
		Set<Long> sellersWithFinal = livePayouts(race.raceId(), PayoutStage.최종_정산).keySet();

		List<PayoutCreateFailure> mismatches = new ArrayList<>();
		for (long sellerId : sellers(soldBySeller.keySet(), payoutsBySeller.keySet())) {
			long sold = soldBySeller.getOrDefault(sellerId, 0L);
			List<Payout> payouts = payoutsBySeller.getOrDefault(sellerId, List.of());
			// 최종이 먼저 만들어졌으면 생성 배치가 1차를 일부러 만들지 않는다. 최종이 전액을 계산했으므로 빠짐이 아니다 (PRO-190)
			if (payouts.isEmpty() && sellersWithFinal.contains(sellerId)) {
				continue;
			}
			long settled = payouts.stream().mapToLong(Payout::getSalesAmount).sum();
			if (sold == settled) {
				continue;
			}
			if (settled > sold) {
				payouts.forEach(payout -> payoutHoldUseCase.holdForReconcileMismatch(payout, now));
			}
			String holdCode = reconcileHoldCodes(payouts);
			PayoutCreateFailureAction action = holdCode != null
				? PayoutCreateFailureAction.지급보류 : PayoutCreateFailureAction.기록만;
			String note = "판매 합 = %,d, 정산 합 = %,d".formatted(sold, settled);
			log.warn("대사_불일치 stage=일차_정산, sellerId={}, raceId={}, {}", sellerId, race.raceId(), note);
			mismatches.add(PayoutCreateFailure.reconcileMismatch(PayoutStage.일차_정산, sellerId, race.raceId(), null,
				race.refundDeadline(), note, action, null, null, holdCode, today));
		}
		return mismatches;
	}

	/**
	 * 최종: 판매자마다 주문 판매 내역의 참가권과 정산 판매 줄을 하나하나 맞춰 본다 (AC-67-4 ~ 7)
	 * - 빠짐: 지급 전 최종 정산 내역이 있으면 판매 줄 · 수수료 줄을 추가한다 (AC-67-14). 정산 내역이 없으면 기록만 한다
	 *   (되돌아보기가 다시 만든다, Q15). 대사는 정산을 만든 그 실행에서 비교하므로 지급 뒤 빠짐은 생기지 않는다
	 * - 더 들어감: 그 참가권이 든 정산 내역을 보류한다 (AC-67-13)
	 * - 가격 다름: 같은 참가권의 주문 판매 가격과 판매 줄 금액이 다르면 기록만 한다 (11/5 결정)
	 */
	@Transactional
	public List<PayoutCreateFailure> reconcileFinal(RaceSchedule race, List<SoldEntry> entries, LocalDate today,
		LocalDateTime now) {
		Map<Long, List<SoldEntry>> soldBySeller = entries.stream().collect(groupingBy(SoldEntry::sellerId));
		Map<Long, List<Payout>> payoutsBySeller = livePayouts(race.raceId(), PayoutStage.최종_정산);

		List<PayoutCreateFailure> mismatches = new ArrayList<>();
		for (long sellerId : sellers(soldBySeller.keySet(), payoutsBySeller.keySet())) {
			Map<Long, SoldEntry> soldById = soldBySeller.getOrDefault(sellerId, List.of()).stream()
				.collect(toMap(SoldEntry::orderItemId, Function.identity()));
			List<Payout> payouts = payoutsBySeller.getOrDefault(sellerId, List.of());
			// 같은 참가권의 판매 줄은 DB 유일 제약으로 하나뿐이다
			Map<Long, PayoutItem> saleById = payouts.stream()
				.flatMap(payout -> payout.getItems().stream())
				.filter(item -> item.getRecordType() == PayoutRecordType.판매)
				.collect(toMap(PayoutItem::getOrderItemId, Function.identity()));

			TicketDiff diff = ticketDiffer.diff(soldById.keySet(), saleById.keySet());
			List<Long> priceDiffers = soldById.keySet().stream()
				.filter(id -> saleById.containsKey(id) && saleById.get(id).getAmount() != soldById.get(id).price())
				.sorted()
				.toList();
			if (diff.isEmpty() && priceDiffers.isEmpty()) {
				continue;
			}

			Payout target = payouts.stream().filter(payout -> payout.getStatus().isBeforePayment()).findFirst()
				.orElse(null);
			boolean added = target != null && !diff.missing().isEmpty();
			if (added) {
				diff.missing().forEach(id -> {
					SoldEntry entry = soldById.get(id);
					target.addItem(id, entry.raceCourseId(), entry.price(), TwoStagePayoutCalculator.FEE_RATE,
						PayoutCreateDate.of(race.raceDate()));
				});
			}
			if (added) {
				payoutLedgerUseCase.settleReceivable(target, now);
			}
			List<Payout> extraPayouts = diff.extra().stream()
				.map(id -> saleById.get(id).getPayout())
				.distinct()
				.toList();
			extraPayouts.forEach(payout -> payoutHoldUseCase.holdForReconcileMismatch(payout, now));
			String holdCode = reconcileHoldCodes(extraPayouts);
			PayoutCreateFailureAction action = holdCode != null ? PayoutCreateFailureAction.지급보류
				: added ? PayoutCreateFailureAction.자동_보정 : PayoutCreateFailureAction.기록만;

			LongFunction<Long> soldCourse = id -> soldById.get(id).raceCourseId();
			LongFunction<Long> saleCourse = id -> saleById.get(id).getRaceCourseId();
			String note = String.join(", ", Stream.of(
					part(added ? "빠진 %d장 추가" : "빠진 %d장", diff.missing(), soldCourse),
					part("더 들어감 %d장", diff.extra(), saleCourse),
					part("가격 다름 %d장", priceDiffers, soldCourse))
				.filter(Objects::nonNull)
				.toList());
			Set<Long> courses = Stream.of(
					diff.missing().stream().map(soldCourse::apply),
					diff.extra().stream().map(saleCourse::apply),
					priceDiffers.stream().map(soldCourse::apply))
				.flatMap(Function.identity())
				.collect(Collectors.toCollection(HashSet::new)); // 코스가 NULL인 참가권도 센다
			// 참가권 ID는 비고 길이를 넘을 수 있어 로그에만 남긴다 (AC-67-12)
			log.warn("대사_불일치 stage=최종_정산, sellerId={}, raceId={}, 빠짐 orderItemId={}, 더 들어감 orderItemId={}, "
				+ "가격 다름 orderItemId={}", sellerId, race.raceId(), diff.missing(), diff.extra(), priceDiffers);
			mismatches.add(PayoutCreateFailure.reconcileMismatch(PayoutStage.최종_정산, sellerId, race.raceId(),
				courses.size() == 1 ? courses.iterator().next() : null, race.raceDate(), truncate(note), action,
				soldById.size(), saleById.size(), holdCode, today));
		}
		return mismatches;
	}

	// 취소된 정산 내역은 비교하지 않는다. 지급보류 · 지급 완료는 비교한다 (5장 실패 시나리오)
	private Map<Long, List<Payout>> livePayouts(long raceId, PayoutStage stage) {
		return payoutRepository.findByRaceId(raceId).stream()
			.filter(payout -> payout.getPayoutStage() == stage && payout.getStatus() != PayoutStatus.취소)
			.collect(groupingBy(Payout::getSellerId));
	}

	// 걸려 있는 대사 불일치 보류의 ID를 쉼표로 잇는다. 없으면(지급 완료라 보류하지 못함) null이라 조치는 기록만이다
	// 어제 이미 같은 사유로 보류해 둔 정산 내역도 그 보류 ID로 지급보류로 본다
	private static String reconcileHoldCodes(List<Payout> payouts) {
		String codes = payouts.stream()
			.map(payout -> payout.activeHoldCode(PayoutHoldReason.대사_불일치))
			.flatMap(Optional::stream)
			.collect(Collectors.joining(", "));
		return codes.isEmpty() ? null : truncate(codes);
	}

	// 판매에만 있는 판매자(빠짐)와 정산에만 있는 판매자(더 들어감)를 모두 본다
	private static Set<Long> sellers(Collection<Long> soldSellers, Collection<Long> payoutSellers) {
		Set<Long> sellers = new TreeSet<>(soldSellers);
		sellers.addAll(payoutSellers);
		return sellers;
	}

	// 예: "빠진 1장 추가 (코스 3: 1장, 코스 4: 2장)". 코스 칸은 한 줄에 하나라 코스별 장수는 비고 뒤에 붙인다 (11/5 사용자)
	private static String part(String label, List<Long> ids, LongFunction<Long> courseOf) {
		if (ids.isEmpty()) {
			return null;
		}
		Map<Long, Integer> countByCourse = new TreeMap<>(Comparator.nullsLast(Comparator.<Long>naturalOrder()));
		ids.forEach(id -> countByCourse.merge(courseOf.apply(id), 1, Integer::sum));
		String courses = countByCourse.entrySet().stream()
			.map(course -> (course.getKey() == null ? "코스 없음" : "코스 " + course.getKey())
				+ ": " + course.getValue() + "장")
			.collect(Collectors.joining(", "));
		return label.formatted(ids.size()) + " (" + courses + ")";
	}

	private static String truncate(String note) {
		return note.length() <= NOTE_MAX_LENGTH ? note : note.substring(0, NOTE_MAX_LENGTH - 1) + "…";
	}
}
