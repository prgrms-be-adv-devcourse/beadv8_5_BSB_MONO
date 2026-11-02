package com.bukang.order.app;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.domain.KitDeliveryStatus;
import com.bukang.order.domain.Order;
import com.bukang.order.domain.OrderItem;
import com.bukang.order.domain.Shipment;
import com.bukang.order.domain.ShipmentStatus;
import com.bukang.order.domain.exception.KitNotShippableException;
import com.bukang.order.domain.exception.OrderAccessDeniedException;
import com.bukang.order.domain.exception.OrderItemNotFoundException;
import com.bukang.order.domain.exception.RaceCourseNotFoundException;
import com.bukang.order.dto.OrderItemDto;
import com.bukang.order.dto.SellerKitPageDto;
import com.bukang.order.dto.SellerKitSummaryDto;
import com.bukang.order.out.OrderItemRepository;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ShipmentRepository;
import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.out.race.RaceCoursePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 판매자의 키트 배송 관리 (PRO-136, 정책 5장 3 키트: 키트는 환불 기한이 지난 뒤 배송하고 배송 상태는 판매자가 바꾼다, 시안 S08)
 * 환불할 수 있는 동안(취소와 같은 기준)은 참가권을 취소할 수 있어 아직 보내지 않는다. 기한이 지난 뒤 보내야 보낸 키트를 돌려받을 일이 없다
 * (대회 취소·일정 변경으로 기한 뒤에 환불돼도 이미 받은 키트는 돌려받지 않는다, 정책 5장 4-3)
 * 상태를 바꾸는 트랜잭션은 이 클래스가 연다. 종목 정보(상품 HTTP)는 주문을 잠그기 전에 묻는다 (OrderCancelUseCase와 같은 방식)
 * sellerId는 판매자 테이블 ID(주문 상품의 seller_id와 같은 값)다. 회원 ID로 판매자 ID를 찾는 일은 부르는 쪽이 한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KitDeliveryUseCase {
	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final ShipmentRepository shipmentRepository;
	private final RaceCoursePort raceCoursePort;
	private final TransactionTemplate transactionTemplate;

	// 발송 대기 → 배송 중. 택배사·송장 번호를 배송(ORDER_SHIPMENT)에 남긴다 (BUK-6)
	public OrderItemDto ship(long sellerId, long orderItemId, String carrier, String trackingNumber,
		LocalDateTime now) {
		// 입력과 주문 상품만 보고 알 수 있는 이유는 모두 상품 서비스에 묻기 전에 알린다
		Shipment.checkShippingInfo(carrier, trackingNumber);
		OrderItem target = transactionTemplate.execute(status -> {
			Order order = findOrder(orderItemId);
			OrderItem item = checkSeller(order.findItem(orderItemId), sellerId);
			order.checkKitShippable(item);
			return item;
		});
		RaceCourseInfo course = raceCoursePort.findCourse(target.getRaceCourseId())
			.orElseThrow(() -> new RaceCourseNotFoundException("존재하지 않는 종목입니다."));
		// 취소와 같은 기준으로, 환불할 수 있는 동안(환불 기한 날까지, 일정 변경 뒤 7일 안, 취소된 대회)은 보내지 않는다
		if (course.isRefundableOn(now.toLocalDate())) {
			throw new KitNotShippableException(
				"아직 환불할 수 있는 참가권이라 발송할 수 없습니다(환불 기한: %s).".formatted(course.refundDeadline()));
		}
		return transactionTemplate.execute(status -> {
			Order order = lockOrder(orderItemId);
			OrderItem item = checkSeller(order.findItem(orderItemId), sellerId);
			order.shipKit(item, carrier, trackingNumber, now);
			return order.toItemDto(item);
		});
	}

	// 배송 중 → 배송 완료
	public OrderItemDto completeDelivery(long sellerId, long orderItemId, LocalDateTime now) {
		return transactionTemplate.execute(status -> {
			Order order = lockOrder(orderItemId);
			OrderItem item = checkSeller(order.findItem(orderItemId), sellerId);
			order.completeKitDelivery(item, now);
			return order.toItemDto(item);
		});
	}

	// 판매자의 키트 주문 목록 한 페이지 (최근 주문 상품부터). raceId·status가 null이면 거르지 않는다
	@Transactional(readOnly = true)
	public SellerKitPageDto findKits(long sellerId, Long raceId, KitDeliveryStatus status, int page, int size) {
		return SellerKitPageDto.from(shipmentRepository.findSellerKits(sellerId, raceId,
			status == null ? null : ShipmentStatus.from(status), PageRequest.of(page, size)));
	}

	// 배송 상태별 건수와, 배송 준비 중 환불 기한 전이라 아직 보낼 수 없는 건수
	public SellerKitSummaryDto summarize(long sellerId, Long raceId, LocalDateTime now) {
		Map<ShipmentStatus, Long> counts = new EnumMap<>(ShipmentStatus.class);
		for (Object[] row : shipmentRepository.countSellerKitsByStatus(sellerId, raceId)) {
			counts.put((ShipmentStatus)row[0], (Long)row[1]);
		}
		long beforeRefundDeadline = 0;
		for (Object[] row : shipmentRepository.countReadyKitsByCourse(sellerId, raceId)) {
			long raceCourseId = (Long)row[0];
			// 상품 서비스에서 사라진 종목 하나 때문에 건수 전체를 못 보이지 않게, 그 종목만 빼고 센다
			Optional<RaceCourseInfo> course = raceCoursePort.findCourse(raceCourseId);
			if (course.isEmpty()) {
				log.warn("키트 건수를 세다 종목을 찾지 못해 환불 기한 전 건수에서 뺍니다. raceCourseId={}", raceCourseId);
				continue;
			}
			if (course.get().isRefundableOn(now.toLocalDate())) {
				beforeRefundDeadline += (Long)row[1];
			}
		}
		return new SellerKitSummaryDto(counts.getOrDefault(ShipmentStatus.READY, 0L),
			counts.getOrDefault(ShipmentStatus.SHIPPING, 0L), counts.getOrDefault(ShipmentStatus.DELIVERED, 0L),
			beforeRefundDeadline);
	}

	private Order findOrder(long orderItemId) {
		return orderRepository.findById(findOrderId(orderItemId)).orElseThrow();
	}

	// 주문을 잠그고 읽는다. 배송 상태를 바꾸는 곳(결제·취소·대회 취소·주소 수정·발송·배송 완료)이 모두 주문을 먼저 잠그므로
	// 같은 참가권의 취소와 겹쳐도 한 요청씩 처리되어, 취소된 참가권은 발송하지 않는다
	private Order lockOrder(long orderItemId) {
		return orderRepository.findByIdForUpdate(findOrderId(orderItemId)).orElseThrow();
	}

	private long findOrderId(long orderItemId) {
		return orderItemRepository.findOrderIdByItemId(orderItemId)
			.orElseThrow(() -> new OrderItemNotFoundException("주문에 없는 상품입니다."));
	}

	private OrderItem checkSeller(OrderItem item, long sellerId) {
		if (!item.isSoldBy(sellerId)) {
			throw new OrderAccessDeniedException("본인 대회의 참가권만 배송 처리할 수 있습니다.");
		}
		return item;
	}
}
