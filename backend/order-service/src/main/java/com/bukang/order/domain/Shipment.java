package com.bukang.order.domain;

import static jakarta.persistence.CascadeType.ALL;
import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;
import com.bukang.order.domain.exception.KitNotShippableException;
import com.bukang.order.domain.exception.KitShippingInfoInvalidException;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 배송. 택배 1건 = 송장 1장 = 상자 1개다 (BUK-6). 상자에 든 주문 상품은 ShipmentItem에 둔다
 * 키트가 있는 참가권을 결제하면 발송 대기로 만들고, 판매자가 송장을 넣으면 배송 중, 받으면 배송 완료다
 * 받는 분·주소는 만들 때 참가 신청의 값을 복사한다. 발송 대기인 동안은 참가자 정보 수정을 따라 바뀌고, 보낸 뒤에는 그대로다
 * 주문(Order)이 묶어 다루므로 상태를 바꾸는 곳은 모두 주문을 잠근 뒤에 부른다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "ORDER_SHIPMENT",
	indexes = {
		// 주문의 배송 찾기 (주문 조회·취소)
		@Index(name = "idx_shipment_order", columnList = "order_id")
	}
)
public class Shipment extends BaseIdAndTime {
	public static final int MAX_CARRIER_LENGTH = 30;
	public static final int MAX_TRACKING_NUMBER_LENGTH = 50;

	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	@Enumerated(STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	private ShipmentStatus status;

	// 판매자가 발송할 때 넣은 택배사·송장 번호 (배송 중부터 있다)
	@Column(name = "carrier", length = MAX_CARRIER_LENGTH)
	private String carrier;

	@Column(name = "tracking_number", length = MAX_TRACKING_NUMBER_LENGTH)
	private String trackingNumber;

	// 받는 분·우편번호·주소·상세 주소 (컬럼 이름은 ShippingAddress의 것)
	@Embedded
	private ShippingAddress shippingAddress;

	// 배송 메모. 아직 받는 곳이 없어 비어 있다
	@Column(name = "delivery_memo", length = 255)
	private String deliveryMemo;

	@Column(name = "shipped_at")
	private LocalDateTime shippedAt;

	@Column(name = "delivered_at")
	private LocalDateTime deliveredAt;

	@Column(name = "canceled_at")
	private LocalDateTime canceledAt;

	@OneToMany(mappedBy = "shipment", cascade = ALL)
	private List<ShipmentItem> items = new ArrayList<>();

	// 주문 상품들을 한 상자로 보낼 발송 대기 배송. 주소는 복사해 참가 신청과 같은 객체를 나눠 쓰지 않는다
	static Shipment ready(Order order, List<OrderItem> orderItems, ShippingAddress shippingAddress) {
		Shipment shipment = new Shipment();
		shipment.order = order;
		shipment.status = ShipmentStatus.READY;
		shipment.shippingAddress = ShippingAddress.copyOf(shippingAddress);
		orderItems.forEach(item -> shipment.items.add(new ShipmentItem(shipment, item)));
		return shipment;
	}

	public boolean contains(OrderItem orderItem) {
		return items.stream().anyMatch(item -> item.getOrderItemId() == orderItem.getId());
	}

	// 발송 대기일 때만 받는 분·주소를 바꾼다. 보낸 뒤(배송 중·완료)와 취소된 배송은 그대로 둔다
	void changeShippingAddressIfReady(ShippingAddress newShippingAddress) {
		if (status == ShipmentStatus.READY) {
			this.shippingAddress = ShippingAddress.copyOf(newShippingAddress);
		}
	}

	// 판매자가 보냈다(발송 대기 → 배송 중). 한 번만 발송한다
	// 참가권이 결제 완료인지는 주문이 먼저 본다 (Order.checkKitShippable)
	void ship(String carrier, String trackingNumber, LocalDateTime shippedAt) {
		if (status != ShipmentStatus.READY) {
			throw new KitNotShippableException("이미 발송한 키트입니다.");
		}
		checkShippingInfo(carrier, trackingNumber);
		this.status = ShipmentStatus.SHIPPING;
		this.carrier = carrier.strip();
		this.trackingNumber = trackingNumber.strip();
		this.shippedAt = shippedAt;
	}

	// 판매자가 보낸 배송인지 (배송 중·배송 완료)
	boolean isShipped() {
		return status == ShipmentStatus.SHIPPING || status == ShipmentStatus.DELIVERED;
	}

	// 구매자가 받았다(배송 중 → 배송 완료). 한 번만 완료한다
	// 참가권 상태는 보지 않는다. 발송 뒤 대회 취소로 환불돼도 이미 보낸 키트는 받게 된다 (정책 5장 4-3)
	void completeDelivery(LocalDateTime deliveredAt) {
		checkDeliverable();
		this.status = ShipmentStatus.DELIVERED;
		this.deliveredAt = deliveredAt;
	}

	void checkDeliverable() {
		if (status == ShipmentStatus.READY || status == ShipmentStatus.CANCELED) {
			throw new KitNotShippableException("아직 발송하지 않은 키트입니다.");
		}
		if (status == ShipmentStatus.DELIVERED) {
			throw new KitNotShippableException("이미 배송 완료한 키트입니다.");
		}
	}

	// 발송 대기인데 상자에 든 참가권이 모두 취소됐으면 배송을 취소한다. 보낸 뒤에는 지금처럼 그대로 둔다
	// 상자에 든 주문 상품은 같은 주문의 것이라 주문이 알려 준다 (Order.cancelItem)
	void cancelIfReadyAndAllCanceled(List<OrderItem> orderItems, LocalDateTime canceledAt) {
		boolean allCanceled = orderItems.stream()
			.filter(this::contains)
			.allMatch(item -> item.getStatus() == OrderItemStatus.CANCELED);
		if (status == ShipmentStatus.READY && allCanceled) {
			this.status = ShipmentStatus.CANCELED;
			this.canceledAt = canceledAt;
		}
	}

	// 택배사·송장 번호가 있고 길이 안인지. 앞뒤 공백은 빼고 센다
	public static void checkShippingInfo(String carrier, String trackingNumber) {
		if (isInvalidText(carrier, MAX_CARRIER_LENGTH) || isInvalidText(trackingNumber, MAX_TRACKING_NUMBER_LENGTH)) {
			throw new KitShippingInfoInvalidException("택배사(%d자 이하)와 송장 번호(%d자 이하)를 넣어 주세요."
				.formatted(MAX_CARRIER_LENGTH, MAX_TRACKING_NUMBER_LENGTH));
		}
	}

	private static boolean isInvalidText(String value, int maxLength) {
		return value == null || value.isBlank() || value.strip().length() > maxLength;
	}
}
