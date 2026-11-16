package com.bukang.order.app;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.order.domain.KitDeliveryStatus;
import com.bukang.order.domain.SellerExportLog;
import com.bukang.order.domain.SellerExportType;
import com.bukang.order.domain.ShipmentStatus;
import com.bukang.order.dto.KitOptionChoiceDto;
import com.bukang.order.dto.SellerKitDto;
import com.bukang.order.dto.ShippingAddressDto;
import com.bukang.order.out.OrderItemRepository;
import com.bukang.order.out.OrderItemRepository.RosterRow;
import com.bukang.order.out.ParticipationKitOptionRepository;
import com.bukang.order.out.SellerExportLogRepository;
import com.bukang.order.out.ShipmentRepository;

import lombok.RequiredArgsConstructor;

/**
 * 판매자 참가자 명단·키트 배송 목록 내려받기 (BUK-228, 시안 S03 명단 내려받기·S08 엑셀로 내려받기)
 * 개인정보(연락처·배송지)가 들어 있어 사유를 받고, 누가 언제 무엇을 몇 줄 받았는지 같은 트랜잭션에서 기록한다
 * 자기 대회 것만 담는다(주문 상품의 판매자 ID로 거름). 남의 대회를 고르면 머리줄만 있는 파일이다
 * 담는 범위(추천값(미확정)): 명단 = 이름·연락처·종목·키트 옵션·파티 번호(조 편성용), 배송 목록 = 키트 배송 관리 목록의 칸
 * shortcut: 한 번에 메모리에서 만든다. 대회 하나 수천 줄까지는 괜찮고, 더 커지면 나눠 쓰기(스트리밍)로 바꾼다
 */
@Service
@RequiredArgsConstructor
public class SellerExportUseCase {
	private static final List<String> ROSTER_HEADER = List.of("주문번호", "종목", "참가자 이름", "연락처", "키트 옵션",
		"파티 번호");
	private static final List<String> KIT_HEADER = List.of("주문번호", "종목", "참가권 상태", "참가자 이름", "받는 분",
		"받는 분 연락처", "우편번호", "주소", "상세 주소", "키트 옵션", "배송 상태", "택배사", "운송장 번호");

	private final OrderItemRepository orderItemRepository;
	private final ShipmentRepository shipmentRepository;
	private final ParticipationKitOptionRepository participationKitOptionRepository;
	private final SellerExportLogRepository sellerExportLogRepository;

	@Transactional
	public SellerExportFile exportRoster(long sellerId, long memberId, long raceId, Long raceCourseId,
		String reason, LocalDate today) {
		List<RosterRow> rows = orderItemRepository.findRoster(sellerId, raceId, raceCourseId);
		Map<Long, List<KitOptionChoiceDto>> options = participationKitOptionRepository.findChoicesByParticipationIds(
			rows.stream().map(RosterRow::participationId).toList());
		List<List<String>> lines = rows.stream()
			.map(row -> List.of(row.orderNo(), row.courseName(), nullToEmpty(row.participantName()),
				nullToEmpty(row.phone()),
				kitOptionText(options.getOrDefault(row.participationId(), List.of()), row.tshirtSize()),
				row.partyId() == null ? "" : String.valueOf(row.partyId())))
			.toList();
		sellerExportLogRepository.save(SellerExportLog.of(sellerId, memberId, raceId, SellerExportType.ROSTER,
			reason.strip(), lines.size()));
		return new SellerExportFile("roster-%d-%s.csv".formatted(raceId, today),
			SellerExportCsv.write(ROSTER_HEADER, lines));
	}

	// 키트 배송 관리 목록(findSellerKits)과 같은 조건·순서로 모두 담는다
	@Transactional
	public SellerExportFile exportKitShipments(long sellerId, long memberId, Long raceId, KitDeliveryStatus status,
		String reason, LocalDate today) {
		List<SellerKitDto> kits = shipmentRepository.findSellerKits(sellerId, raceId,
			status == null ? null : ShipmentStatus.from(status), Pageable.unpaged()).getContent();
		Map<Long, List<KitOptionChoiceDto>> options = participationKitOptionRepository.findChoicesByParticipationIds(
			kits.stream().map(SellerKitDto::getParticipationId).toList());
		List<List<String>> lines = kits.stream()
			.map(kit -> kitLine(kit, options.getOrDefault(kit.getParticipationId(), List.of())))
			.toList();
		sellerExportLogRepository.save(SellerExportLog.of(sellerId, memberId, raceId, SellerExportType.KIT_SHIPMENTS,
			reason.strip(), lines.size()));
		String scope = raceId == null ? "all" : String.valueOf(raceId);
		return new SellerExportFile("kit-shipments-%s-%s.csv".formatted(scope, today),
			SellerExportCsv.write(KIT_HEADER, lines));
	}

	private static List<String> kitLine(SellerKitDto kit, List<KitOptionChoiceDto> options) {
		ShippingAddressDto address = kit.getShippingAddress();
		return List.of(kit.getOrderNo(), kit.getCourseName(), kit.getOrderItemStatus().getLabel(),
			nullToEmpty(kit.getParticipantName()),
			address == null ? "" : nullToEmpty(address.getRecipientName()),
			address == null ? "" : nullToEmpty(address.getRecipientPhone()),
			address == null ? "" : nullToEmpty(address.getZipcode()),
			address == null ? "" : nullToEmpty(address.getAddress()),
			address == null ? "" : nullToEmpty(address.getAddressDetail()),
			kitOptionText(options, kit.getTshirtSize()), kit.getKitDeliveryStatus().getLabel(),
			nullToEmpty(kit.getKitCarrier()), nullToEmpty(kit.getKitTrackingNumber()));
	}

	// 예: "T-shirt: L / 양말: M". 옵션 전에 결제한 신청은 옛 티셔츠 사이즈를 쓴다
	static String kitOptionText(List<KitOptionChoiceDto> options, String tshirtSize) {
		if (options.isEmpty()) {
			return tshirtSize == null ? "" : "티셔츠: " + tshirtSize;
		}
		return options.stream()
			.map(option -> option.name() + ": " + option.value())
			.collect(Collectors.joining(" / "));
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
