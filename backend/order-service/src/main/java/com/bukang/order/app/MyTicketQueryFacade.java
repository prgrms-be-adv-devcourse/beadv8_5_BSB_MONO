package com.bukang.order.app;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.bukang.order.domain.Participation;
import com.bukang.order.domain.ParticipationKitOption;
import com.bukang.order.domain.Party;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.dto.KitOptionChoiceDto;
import com.bukang.order.dto.MyTicketDto;
import com.bukang.order.dto.MyTicketPageDto;
import com.bukang.order.out.OrderItemRepository;
import com.bukang.order.out.OrderItemRepository.TicketOrder;
import com.bukang.order.out.ParticipationKitOptionRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.PartyRepository;
import com.bukang.order.out.ShipmentRepository;
import com.bukang.order.out.ShipmentRepository.TicketShipment;
import com.bukang.order.out.member.MemberNicknamesPort;
import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.out.race.RaceCoursePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 내 참가권 목록 (BUK-181). 신청 한 페이지를 읽고, 주문·배송·키트 옵션·파티는 페이지 전체를 한 번씩 모아 읽는다 (N+1 없음)
 * 트랜잭션을 걸지 않는다. 상품·회원 서비스를 기다리는 동안 DB 연결을 잡지 않으려고 저장소 조회마다 짧게 읽는다 (PartyQueryFacade와 같음)
 * 상품·회원 서비스에 묻지 못해도 목록은 보여 주고 그 칸만 비운다. 목록 화면 하나 때문에 내 참가권을 못 보게 하지 않으려는 것이다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MyTicketQueryFacade {
	private final ParticipationRepository participationRepository;
	private final OrderItemRepository orderItemRepository;
	private final ShipmentRepository shipmentRepository;
	private final ParticipationKitOptionRepository participationKitOptionRepository;
	private final PartyRepository partyRepository;
	private final RaceCoursePort raceCoursePort;
	private final MemberNicknamesPort memberNicknamesPort;

	public MyTicketPageDto findMine(long memberId, boolean activeOnly, int page, int size) {
		PageRequest pageable = PageRequest.of(page, size);
		Page<Participation> participations = activeOnly
			? participationRepository.findByMemberIdAndActiveFlagTrueOrderByIdDesc(memberId, pageable)
			: participationRepository.findByMemberIdOrderByIdDesc(memberId, pageable);
		List<Participation> rows = participations.getContent();
		if (rows.isEmpty()) {
			return toPage(participations, List.of());
		}
		List<Long> participationIds = rows.stream().map(Participation::getId).toList();

		Map<Long, TicketOrder> orders = orderItemRepository.findTicketOrders(participationIds).stream()
			.collect(Collectors.toMap(TicketOrder::participationId, Function.identity()));
		// 재발송이면 한 상품이 여러 배송에 든다. 배송 ID 순으로 오므로 나중 것이 남는다
		Map<Long, TicketShipment> shipments = orders.isEmpty() ? Map.of()
			: shipmentRepository.findTicketShipments(orders.values().stream().map(TicketOrder::orderId).toList())
			.stream()
			.collect(Collectors.toMap(TicketShipment::orderItemId, Function.identity(), (older, newer) -> newer));
		Map<Long, List<KitOptionChoiceDto>> kitOptions = participationKitOptionRepository
			.findByParticipationIdInOrderByIdAsc(participationIds).stream()
			.collect(Collectors.groupingBy(ParticipationKitOption::getParticipationId,
				Collectors.mapping(KitOptionChoiceDto::from, Collectors.toList())));
		List<Long> partyIds = rows.stream().map(Participation::getPartyId).filter(Objects::nonNull).distinct().toList();
		// HashMap이라 개인 신청(partyId null)으로 찾아도 null이 나온다
		Map<Long, Party> parties = partyRepository.findAllById(partyIds).stream()
			.collect(Collectors.toMap(Party::getId, Function.identity()));
		Map<Long, String> nicknames = nicknames(parties.values().stream().map(Party::getLeaderMemberId).distinct()
			.toList());
		Map<Long, RaceCourseInfo> courses = courses(rows.stream().map(Participation::getRaceCourseId).distinct()
			.toList());

		List<MyTicketDto> tickets = rows.stream().map(participation -> toDto(participation,
			orders.get(participation.getId()), shipments, kitOptions.getOrDefault(participation.getId(), List.of()),
			parties.get(participation.getPartyId()), nicknames, courses.get(participation.getRaceCourseId())))
			.toList();
		return toPage(participations, tickets);
	}

	// shortcut: 상품에 종목 여러 개를 한 번에 묻는 API가 없어 페이지 안의 서로 다른 종목마다 한 번씩 묻는다(최대 페이지 크기만큼).
	// 상품이 묶음 조회 API를 열면 한 번으로 바꾼다
	private Map<Long, RaceCourseInfo> courses(List<Long> raceCourseIds) {
		Map<Long, RaceCourseInfo> courses = new HashMap<>();
		for (long raceCourseId : raceCourseIds) {
			Optional<RaceCourseInfo> course;
			try {
				course = raceCoursePort.findCourse(raceCourseId);
			} catch (ExternalServiceUnavailableException exception) {
				// 한 번 실패하면 남은 종목도 실패할 가능성이 커 더 묻지 않는다 (페이지가 실패 대기 시간만큼 늦어지지 않게)
				log.warn("내 참가권 목록: 상품 서비스에 묻지 못해 대회 칸을 비웁니다. raceCourseId={}", raceCourseId);
				return courses;
			}
			course.ifPresent(found -> courses.put(raceCourseId, found));
		}
		return courses;
	}

	private Map<Long, String> nicknames(Collection<Long> memberIds) {
		if (memberIds.isEmpty()) {
			return Map.of();
		}
		try {
			Map<Long, String> found = memberNicknamesPort.findNicknames(memberIds);
			Map<Long, String> nicknames = new HashMap<>();
			memberIds.forEach(id -> nicknames.put(id, found.getOrDefault(id, MemberNicknamesPort.WITHDRAWN_NICKNAME)));
			return nicknames;
		} catch (ExternalServiceUnavailableException exception) {
			log.warn("내 참가권 목록: 회원 서비스에 묻지 못해 파티장 닉네임을 비웁니다. memberIds={}", memberIds);
			return Map.of();
		}
	}

	private static MyTicketDto toDto(Participation participation, TicketOrder order,
		Map<Long, TicketShipment> shipments, List<KitOptionChoiceDto> kitOptions, Party party,
		Map<Long, String> nicknames, RaceCourseInfo course) {
		TicketShipment shipment = order == null ? null : shipments.get(order.orderItemId());
		return new MyTicketDto(
			participation.getId(),
			participation.getCreateDate(),
			participation.getRaceId(),
			participation.getRaceCourseId(),
			course == null ? null : course.raceName(),
			course != null ? course.courseName() : order == null ? null : order.itemName(),
			course != null ? course.raceDate() : order == null ? null : order.raceDate(),
			course == null ? null : course.refundDeadline(),
			participation.getParticipant().getName(),
			participation.getStatus(),
			participation.getParticipationType(),
			participation.getPaymentDueAt(),
			order == null ? null : order.orderId(),
			order == null ? null : order.orderNo(),
			order == null ? null : order.orderStatus(),
			order == null ? null : order.orderItemId(),
			order == null ? null : order.status(),
			participation.getCanceledAt(),
			order == null ? null : order.amount(),
			kitOptions,
			shipment == null ? null : shipment.status().getKitDeliveryStatus(),
			shipment == null ? null : shipment.carrier(),
			shipment == null ? null : shipment.trackingNumber(),
			party == null ? null : party.getId(),
			party == null ? null : party.getCrewId(),
			party == null ? null : party.getStatus(),
			party == null ? null : party.getLeaderMemberId(),
			party == null ? null : nicknames.get(party.getLeaderMemberId())
		);
	}

	private static MyTicketPageDto toPage(Page<Participation> page, List<MyTicketDto> tickets) {
		return new MyTicketPageDto(tickets, page.getNumber(), page.getSize(), page.getTotalElements(),
			page.getTotalPages());
	}
}
