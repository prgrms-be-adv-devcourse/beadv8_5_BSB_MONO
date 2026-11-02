package com.bukang.order.domain;

import static jakarta.persistence.CascadeType.ALL;
import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;
import com.bukang.order.domain.event.OrderExpiredEvent;
import com.bukang.order.domain.event.OrderRaceCanceledEvent;
import com.bukang.order.domain.exception.KitNotShippableException;
import com.bukang.order.domain.exception.OrderItemNotFoundException;
import com.bukang.order.domain.exception.OrderNotPayableException;
import com.bukang.order.dto.OrderDto;
import com.bukang.order.dto.OrderItemDto;
import com.bukang.order.dto.OrderSummaryDto;
import com.bukang.order.dto.ShippingAddressDto;
import com.bukang.order.out.race.RaceCourseInfo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문. 개인 예매는 결제 1건 = 참가 신청 1건이라 주문 상품이 1개다 (ERD 추천안 v4 연결점, 09 ERD orders)
 * 금액은 주문 당시 값을 남긴다. 구매자·대회는 다른 서비스 소유라 FK 없이 ID 값만 둔다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "ORDER_ORDER",
	uniqueConstraints = {
		// 같은 요청을 두 번 보내도 주문은 하나 (정책 4장 2-3)
		@UniqueConstraint(name = "uk_order_idempotency", columnNames = {"buyer_id", "idempotency_key"})
	},
	indexes = {
		// 내 주문 목록 최신순 (PRO-43)
		@Index(name = "idx_order_buyer", columnList = "buyer_id, id"),
		// 결제 기한이 지난 결제 대기 주문 찾기 (만료 정리 배치, PRO-42)
		@Index(name = "idx_order_due", columnList = "status, payment_due_at"),
		// 취소된 대회의 주문 찾기 (일괄 환불, PRO-135)
		@Index(name = "idx_order_race", columnList = "race_id, id")
	}
)
public class Order extends BaseIdAndTime {
	private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

	// 주문 번호: OD-주문한 날-16진수 4자리 (예: OD-20261012-3F9A). 정산 ID(ST-)와 같은 모양, ID를 받은 뒤 정한다
	@Column(name = "order_no", length = 30)
	private String orderNo;

	@Column(name = "buyer_id", nullable = false)
	private long buyerId;

	@Column(name = "race_id", nullable = false)
	private long raceId;

	@Column(name = "total_amount", nullable = false)
	private long totalAmount;

	@Column(name = "canceled_amount", nullable = false)
	private long canceledAmount;

	@Enumerated(STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	private OrderStatus status;

	@Column(name = "idempotency_key", nullable = false, length = 64)
	private String idempotencyKey;

	// 화면·정리용 사본. 결제와 만료 판정의 원본은 상품 서비스의 RACE_SEAT.expires_at이다
	@Column(name = "payment_due_at", nullable = false)
	private LocalDateTime paymentDueAt;

	@Column(name = "paid_at")
	private LocalDateTime paidAt;

	// 주문 목록(PRO-43)에서 주문마다 상품을 따로 읽지 않도록 여러 주문의 상품을 한 번에 읽는다
	@BatchSize(size = 100)
	@OneToMany(mappedBy = "order", cascade = ALL)
	private List<OrderItem> items = new ArrayList<>();

	// 키트 배송 (BUK-6). 키트가 있는 참가권을 결제하면 1건 생긴다. 배송지는 주문이 아니라 배송에 둔다
	// DB의 옛 배송지 컬럼(recipient_name ~ address_detail)은 값을 배송으로 옮긴 뒤 다음 배포에서 지운다
	// 만든 순서로 읽는다. 재발송이 생기면 마지막 배송이 지금 배송이다 (findKitShipment)
	@BatchSize(size = 100)
	@OneToMany(mappedBy = "order", cascade = ALL)
	@OrderBy("id")
	private List<Shipment> shipments = new ArrayList<>();

	private Order(long buyerId, long raceId, String idempotencyKey, LocalDateTime paymentDueAt) {
		this.buyerId = buyerId;
		this.raceId = raceId;
		this.idempotencyKey = idempotencyKey;
		this.paymentDueAt = paymentDueAt;
		this.status = OrderStatus.CREATED;
	}

	// 바로 결제 예매: 자리를 확보한 참가 신청 1건으로 주문 상품 1개짜리 주문을 만든다
	public static Order forParticipation(Participation participation, RaceCourseInfo course, String idempotencyKey,
		LocalDateTime paymentDueAt) {
		Order order = new Order(participation.getMemberId(), course.raceId(), idempotencyKey, paymentDueAt);
		order.items.add(OrderItem.ticket(order, participation, course));
		order.totalAmount = order.items.stream().mapToLong(OrderItem::getAmount).sum();
		return order;
	}

	// 참가자 정보에서 배송지를 고치면 아직 보내지 않은(발송 대기) 배송의 받는 분·주소도 바꾼다
	// 이미 보낸 배송은 보낸 곳 그대로 둔다 (BUK-6)
	public void changeKitShippingAddress(ShippingAddress newShippingAddress) {
		shipments.forEach(shipment -> shipment.changeShippingAddressIfReady(newShippingAddress));
	}

	// 저장해 ID를 받은 뒤 부른다
	public void assignOrderNo() {
		this.orderNo = "OD-%s-%04X".formatted(getCreateDate().format(NO_DATE), getId() & 0xFFFF);
	}

	// 이 주문이 해당 참가 신청으로 만든 주문인지 (같은 Idempotency-Key를 다른 신청서에 다시 쓴 요청 가려내기)
	public boolean isFor(long participationId) {
		return items.stream().anyMatch(item -> item.getParticipationId() == participationId);
	}

	// 결제할 수 있는지: 결제 대기 중이고 결제 기한(사본) 전이어야 한다. 최종 판정은 상품 서비스의 자리 판매가 한다
	public void checkPayable(LocalDateTime now) {
		// 만료 정리(1분마다)가 먼저 돌아 EXPIRED가 됐어도 회원에게는 같은 "기한 지남"으로 알린다
		if (status == OrderStatus.EXPIRED) {
			throw OrderNotPayableException.deadlinePassed("결제 기한이 지났습니다.");
		}
		if (status != OrderStatus.CREATED) {
			throw new OrderNotPayableException(
				"결제할 수 없는 주문입니다(현재 상태: %s).".formatted(status.getLabel()));
		}
		if (!now.isBefore(paymentDueAt)) {
			throw OrderNotPayableException.deadlinePassed("결제 기한이 지났습니다.");
		}
	}

	public boolean isPaid() {
		return status == OrderStatus.PAID;
	}

	// 결제 기한이 지난 결제 대기 주문인지 (만료 정리 대상, OrderExpiryUseCase). 기한 시각부터 지난 것으로 본다
	public boolean isOverdue(LocalDateTime now) {
		return status == OrderStatus.CREATED && !now.isBefore(paymentDueAt);
	}

	// 결제하지 않고 기한이 지났다. 예치금 결제 취소를 부른 뒤에 바꾼다 (OrderExpiryUseCase)
	// 상품 서비스에 자리를 풀어 달라고 알리도록 이벤트를 발행한다. 같은 트랜잭션에서 아웃박스에 남는다 (OrderExpiredEventListener)
	public void expire(LocalDateTime now) {
		if (!isOverdue(now)) {
			throw new IllegalStateException(
				"결제 기한이 지난 결제 대기 주문만 만료할 수 있습니다(현재 상태: %s).".formatted(status.getLabel()));
		}
		this.status = OrderStatus.EXPIRED;
		publishEvent(new OrderExpiredEvent(getId(), participationId()));
	}

	// 예치금 차감과 자리 판매가 끝났다. 주문과 주문 상품을 함께 결제 완료로 바꾼다
	// 키트가 있으면 주문 상품을 한 상자로 보낼 발송 대기 배송을 만든다 (BUK-6)
	// kitShippingAddress는 참가 신청의 배송지다. 키트가 없는 종목은 신청서에 배송지를 남기지 않아 null이다
	// (ParticipationApplyUseCase.checkShippingAddress가 신청·수정 때 키트 여부에 맞춰 넣고 뺀다)
	public void markPaid(LocalDateTime paidAt, ShippingAddress kitShippingAddress) {
		if (status != OrderStatus.CREATED) {
			throw new OrderNotPayableException(
				"결제할 수 없는 주문입니다(현재 상태: %s).".formatted(status.getLabel()));
		}
		this.status = OrderStatus.PAID;
		this.paidAt = paidAt;
		items.forEach(OrderItem::markPaid);
		if (kitShippingAddress != null) {
			shipments.add(Shipment.ready(this, items, kitShippingAddress));
		}
	}

	public OrderItem findItem(long orderItemId) {
		return items.stream()
			.filter(item -> item.getId() == orderItemId)
			.findFirst()
			.orElseThrow(() -> new OrderItemNotFoundException("주문에 없는 상품입니다."));
	}

	// 주문 상품 취소를 시작한다(결제 완료 → 취소 중). 주문 상태와 배송은 환불이 확인돼 마무리할 때(cancelItem) 바꾼다 (BUK-9)
	public void startCancelItem(OrderItem item, LocalDateTime requestedAt) {
		item.startCancel(requestedAt);
	}

	// 예치금이 환불을 거절해 취소 중을 결제 완료로 되돌린다. 주문 상태·배송은 시작 때 바꾸지 않았으므로 그대로다
	public void revertCancelItem(OrderItem item) {
		item.revertCancel();
	}

	// 참가 신청의 주문 상품이 취소 중인지. 환불 결과를 기다리는 동안 참가자 정보를 고치지 못하게 한다
	public boolean isCanceling(long participationId) {
		return items.stream()
			.anyMatch(item -> item.getParticipationId() == participationId && item.isCanceling());
	}

	// 주문 상품 하나를 취소한다. 모두 취소되면 CANCELED, 일부만이면 PARTIAL_CANCELED (정책 5장, ERD D3)
	// 아직 보내지 않은 키트 배송도 함께 취소해 판매자 목록에서 뺀다. 보낸 배송은 그대로 둔다 (BUK-6)
	public void cancelItem(OrderItem item, LocalDateTime canceledAt) {
		item.cancel(canceledAt);
		shipments.stream()
			.filter(shipment -> shipment.contains(item))
			.forEach(shipment -> shipment.cancelIfReadyAndAllCanceled(items, canceledAt));
		this.canceledAmount += item.getAmount();
		boolean allCanceled = items.stream().allMatch(each -> each.getStatus() == OrderItemStatus.CANCELED);
		this.status = allCanceled ? OrderStatus.CANCELED : OrderStatus.PARTIAL_CANCELED;
	}

	// 대회가 취소됐다. 결제한 주문 상품을 모두 취소하고 취소한 상품을 돌려준다 (키트 포함 전액, 정책 1차 5장 4번)
	// 결제 전이거나 이미 취소한 상품은 그대로 둔다. 같은 취소가 다시 와도 빈 목록이라 환불이 한 번만 일어난다
	// 취소 중인 상품도 건너뛴다. 사용자 취소의 재처리가 같은 주문 ID로 환불하고 마무리한다 (BUK-9)
	// 취소한 상품이 있으면 구매자에게 알리도록 이벤트를 발행한다 (환불까지 커밋된 뒤에 알린다)
	public List<OrderItem> cancelPaidItems(LocalDateTime canceledAt) {
		List<OrderItem> paidItems = items.stream()
			.filter(item -> item.getStatus() == OrderItemStatus.PAID)
			.toList();
		paidItems.forEach(item -> cancelItem(item, canceledAt));
		if (!paidItems.isEmpty()) {
			long refundedAmount = paidItems.stream().mapToLong(OrderItem::getAmount).sum();
			publishEvent(new OrderRaceCanceledEvent(getId(), buyerId, raceId, refundedAmount));
		}
		return paidItems;
	}

	// 판매자가 키트를 보냈다(발송 대기 → 배송 중). 결제한 참가권의 키트만, 한 번만 발송한다
	// 환불 기한이 지났는지는 종목 정보를 알아야 해서 부르는 쪽(KitDeliveryUseCase)이 확인한다
	public void shipKit(OrderItem item, String carrier, String trackingNumber, LocalDateTime shippedAt) {
		checkKitShippable(item);
		findKitShipment(item).ship(carrier, trackingNumber, shippedAt);
	}

	// 키트를 보낼 수 있는 주문 상품인지 (결제 완료, 키트 있음, 아직 안 보냄)
	public void checkKitShippable(OrderItem item) {
		Shipment shipment = findKitShipment(item);
		if (shipment == null && item.getStatus() == OrderItemStatus.PAID) {
			throw new KitNotShippableException("키트가 없는 참가권입니다.");
		}
		if (shipment != null && shipment.isShipped()) {
			throw new KitNotShippableException("이미 발송한 키트입니다.");
		}
		if (item.getStatus() != OrderItemStatus.PAID) {
			throw new KitNotShippableException(
				"결제한 참가권의 키트만 발송할 수 있습니다(현재 상태: %s).".formatted(item.getStatus().getLabel()));
		}
	}

	// 구매자가 키트를 받았다(배송 중 → 배송 완료). 보낸 키트만, 한 번만 완료한다
	public void completeKitDelivery(OrderItem item, LocalDateTime deliveredAt) {
		Shipment shipment = findKitShipment(item);
		if (shipment == null) {
			throw new KitNotShippableException(item.getStatus() == OrderItemStatus.PAID
				? "키트가 없는 참가권입니다." : "아직 발송하지 않은 키트입니다.");
		}
		shipment.completeDelivery(deliveredAt);
	}

	// 주문 상품이 든 배송. 재발송이 생기면 여러 건일 수 있어 가장 나중 것을 쓴다. 키트가 없거나 결제 전이면 null
	private Shipment findKitShipment(OrderItem item) {
		return shipments.reversed().stream()
			.filter(shipment -> shipment.contains(item))
			.findFirst()
			.orElse(null);
	}

	// 개인 예매 주문은 참가 신청 1건짜리다 (결제 1건 = 참가 신청 1건)
	public long participationId() {
		return items.getFirst().getParticipationId();
	}

	public boolean isOwnedBy(long memberId) {
		return buyerId == memberId;
	}

	// 결제해 배송이 생긴 주문의 응답. 배송이 없으면(결제 전·키트 없음) 배송지는 비어 있다
	public OrderDto toDto() {
		return toDto(null);
	}

	/**
	 * 주문 응답. 배송지·키트 배송 칸은 배송에서 채운다 (BUK-6)
	 * 결제 전이라 배송이 아직 없으면 applicationShippingAddress(참가 신청의 배송지)를 보여 주고,
	 * 그 배송지가 있으면(키트 있는 종목) 키트 배송 상태를 배송 준비로 보낸다. 배송 표 전과 같은 응답이다
	 */
	public OrderDto toDto(ShippingAddress applicationShippingAddress) {
		Shipment latest = shipments.isEmpty() ? null : shipments.getLast();
		ShippingAddress shippingAddress = latest == null ? applicationShippingAddress : latest.getShippingAddress();
		KitDeliveryStatus kitWithoutShipment = applicationShippingAddress == null ? null : KitDeliveryStatus.PREPARING;
		return new OrderDto(
			getId(),
			getCreateDate(),
			orderNo,
			buyerId,
			raceId,
			status,
			totalAmount,
			canceledAmount,
			paymentDueAt,
			paidAt,
			ShippingAddressDto.from(shippingAddress),
			items.stream().map(item -> item.toDto(kitWithoutShipment, findKitShipment(item))).toList()
		);
	}

	// 주문 상품 하나의 응답 (판매자 키트 발송·배송 완료)
	public OrderItemDto toItemDto(OrderItem item) {
		return item.toDto(null, findKitShipment(item));
	}

	public OrderSummaryDto toSummaryDto() {
		return new OrderSummaryDto(getId(), getCreateDate(), orderNo, status, totalAmount, paymentDueAt,
			items.getFirst().getItemName(), items.size());
	}
}
