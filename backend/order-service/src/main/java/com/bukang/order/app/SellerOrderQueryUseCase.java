package com.bukang.order.app;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.bukang.order.domain.OrderItemStatus;
import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.domain.Party;
import com.bukang.order.domain.exception.OrderSearchPeriodException;
import com.bukang.order.domain.exception.RaceCourseNotFoundException;
import com.bukang.order.domain.exception.SellerAccessDeniedException;
import com.bukang.order.dto.KitOptionChoiceDto;
import com.bukang.order.dto.SellerOrderDto;
import com.bukang.order.dto.SellerOrderPageDto;
import com.bukang.order.dto.SellerPartyDto;
import com.bukang.order.dto.SellerPartyPageDto;
import com.bukang.order.out.OrderItemRepository;
import com.bukang.order.out.ParticipationKitOptionRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.PartyRepository;
import com.bukang.order.out.race.RaceCoursePort;

import lombok.RequiredArgsConstructor;

/**
 * 판매자 주문 목록과 파티별 내역 (BUK-227, 시안 S03·S03-1·S05)
 * 주문 목록은 주문 상품의 판매자 ID로 거르므로 자기 대회 것만 나온다
 * 파티에는 판매자 ID가 없어, 파티 종목의 판매자를 상품 서비스에 물어 확인한다
 * (파티는 대회의 종목으로 만들고 종목을 바꾸지 않으므로(PartyUseCase) 한 파티만 물으면 된다. 대회의 종목은 모두 같은 판매자라고 본다)
 * 상품 HTTP가 DB 연결을 쥔 채 일어나지 않게 트랜잭션을 열지 않는다(저장소 호출마다 짧게 읽는다)
 */
@Service
@RequiredArgsConstructor
public class SellerOrderQueryUseCase {
	// 정책 6장 조회·보관: 조회 기간은 한 번에 최대 12개월
	static final int MAX_PERIOD_MONTHS = 12;

	private final OrderItemRepository orderItemRepository;
	private final ParticipationKitOptionRepository participationKitOptionRepository;
	private final PartyRepository partyRepository;
	private final ParticipationRepository participationRepository;
	private final RaceCoursePort raceCoursePort;

	/**
	 * 판매자 주문 목록 한 페이지 (최근 주문 상품부터). 대시보드 최근 주문도 같은 목록을 작은 페이지로 쓴다
	 * 파티 번호(partyId)로 찾으면 기간은 보지 않는다 (기능명세 S-25, 정책 6장 조회·보관-2)
	 */
	public SellerOrderPageDto findOrders(long sellerId, Long raceId, Long raceCourseId, OrderItemStatus status,
		Long partyId, LocalDate from, LocalDate to, int page, int size) {
		if (partyId != null) {
			from = null;
			to = null;
		}
		checkPeriod(from, to);
		Page<SellerOrderDto> orders = orderItemRepository.findSellerOrders(sellerId, raceId, raceCourseId, status,
			partyId, startOf(from), endOf(to), PageRequest.of(page, size));
		Map<Long, List<KitOptionChoiceDto>> options = participationKitOptionRepository.findChoicesByParticipationIds(
			orders.stream().map(SellerOrderDto::getParticipationId).toList());
		orders.forEach(order -> order.attachKitOptions(options.getOrDefault(order.getParticipationId(), List.of())));
		return SellerOrderPageDto.from(orders);
	}

	/**
	 * 대회의 파티별 신청·결제 인원 한 페이지 (최근 파티부터). 파티 번호로 찾으면 기간은 보지 않는다
	 * 인원은 파티들의 신청서를 한 번에 세어 붙인다. 결제한 뒤 취소한 신청서(CANCELED)는 세지 않는다(추천값(미확정))
	 */
	public SellerPartyPageDto findParties(long sellerId, long raceId, Long partyId, LocalDate from, LocalDate to,
		int page, int size) {
		if (partyId != null) {
			from = null;
			to = null;
		}
		checkPeriod(from, to);
		// 대회 소유는 조회 결과와 상관없이 먼저 본다. 결과가 빌 때 건너뛰면 남의 대회 파티 수(totalElements)와
		// 파티 ID가 그 대회 것인지가 샌다. 신청한 파티가 하나도 없는 대회는 보여 줄 것이 없어 빈 목록이다
		Party anyParty = partyRepository.findFirstByRaceIdAndAppliedAtIsNotNull(raceId).orElse(null);
		if (anyParty == null) {
			return SellerPartyPageDto.from(Page.<SellerPartyDto>empty(PageRequest.of(page, size)));
		}
		checkPartySeller(anyParty, sellerId);
		Page<Party> parties = partyRepository.findAppliedByRaceId(raceId, partyId, startOf(from), endOf(to),
			PageRequest.of(page, size));
		if (parties.isEmpty()) {
			return SellerPartyPageDto.from(parties.map(party -> (SellerPartyDto)null));
		}
		Map<Long, Map<ParticipationStatus, Long>> counts = new HashMap<>();
		for (Object[] row : participationRepository.countByPartyIdsAndStatus(
			parties.map(Party::getId).getContent())) {
			counts.computeIfAbsent((Long)row[0], id -> new HashMap<>()).put((ParticipationStatus)row[1], (Long)row[2]);
		}
		return SellerPartyPageDto.from(parties.map(party -> {
			Map<ParticipationStatus, Long> byStatus = counts.getOrDefault(party.getId(), Map.of());
			long paid = byStatus.getOrDefault(ParticipationStatus.CONFIRMED, 0L);
			long unpaid = byStatus.getOrDefault(ParticipationStatus.APPLIED, 0L)
				+ byStatus.getOrDefault(ParticipationStatus.RELEASED, 0L);
			return new SellerPartyDto(party.getId(), party.getCrewId(), party.getRaceCourseId(), party.getStatus(),
				paid + unpaid, paid, unpaid, party.getPaymentDueAt());
		}));
	}

	private void checkPartySeller(Party party, long sellerId) {
		long courseSellerId = raceCoursePort.findCourse(party.getRaceCourseId())
			.orElseThrow(() -> new RaceCourseNotFoundException("존재하지 않는 종목입니다."))
			.sellerId();
		if (courseSellerId != sellerId) {
			throw new SellerAccessDeniedException("본인 대회의 파티만 볼 수 있습니다.");
		}
	}

	static void checkPeriod(LocalDate from, LocalDate to) {
		if (from == null || to == null) {
			return;
		}
		if (from.isAfter(to)) {
			throw new OrderSearchPeriodException("조회 시작일이 종료일보다 늦습니다.");
		}
		if (to.isAfter(from.plusMonths(MAX_PERIOD_MONTHS))) {
			throw new OrderSearchPeriodException("조회 기간은 한 번에 12개월까지입니다.");
		}
	}

	private static LocalDateTime startOf(LocalDate from) {
		return from == null ? null : from.atStartOfDay();
	}

	// 종료일 하루 전체를 넣으려고 다음 날 0시 미만으로 찾는다
	private static LocalDateTime endOf(LocalDate to) {
		return to == null ? null : to.plusDays(1).atStartOfDay();
	}
}
