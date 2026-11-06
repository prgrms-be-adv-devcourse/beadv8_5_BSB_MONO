package com.bukang.order.domain;

import static jakarta.persistence.CascadeType.ALL;
import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;
import com.bukang.order.domain.exception.KitNotShippableException;
import com.bukang.order.domain.exception.KitShippingInfoInvalidException;
import com.bukang.order.dto.KitTrackingDto;
import com.bukang.order.out.delivery.DeliveryTrackingResult;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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
		@Index(name = "idx_shipment_order", columnList = "order_id"),
		// 조회 배치가 배송 중인 배송을 고른다 (BUK-55). BUK-90부터는 status로 거른 뒤 next_tracking_at으로 고른다
		@Index(name = "idx_shipment_tracking", columnList = "status, last_tracked_at")
	}
)
public class Shipment extends BaseIdAndTime {
	// 택배사 이름 칸 길이 (BUK-55 전에는 판매자가 직접 적었다)
	public static final int MAX_CARRIER_LENGTH = 30;
	public static final int MAX_TRACKING_NUMBER_LENGTH = 50;
	private static final int MAX_TRACKING_STATUS_TEXT_LENGTH = 50;
	// 발송(또는 송장 수정) 뒤 이만큼 지나도 택배사가 스캔하지 않으면 조회를 멈춘다 (BUK-90, 11/05 R4)
	public static final Duration NOT_TRACKED_LIMIT = Duration.ofDays(7);

	// 조회 결과를 남긴 뒤의 상태. STOPPED는 이번에 조회를 멈췄다는 뜻이라 판매자에게 한 번 알린다
	public enum TrackingOutcome {
		TRACKED, DELIVERED, STOPPED
	}

	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	@Enumerated(STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	private ShipmentStatus status;

	// 판매자가 발송할 때 고른 택배사의 이름·송장 번호 (배송 중부터 있다)
	@Column(name = "carrier", length = MAX_CARRIER_LENGTH)
	private String carrier;

	// 택배사의 Delivery Tracker ID (BUK-55). 이 변경 전에 보낸 배송은 비어 있어 자동 조회하지 않는다
	@Column(name = "carrier_id", length = 50)
	private String carrierId;

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

	// 마지막으로 받아 온 배송 상태와 택배사가 붙인 상태 이름, 그 상태의 시각 (BUK-55). 발송하면 아직 조회되지 않음이다
	// 화면은 이 값을 보여 주므로 Delivery Tracker가 멈춰도 주문 화면은 뜬다
	@Enumerated(STRING)
	@Column(name = "tracking_status", columnDefinition = "varchar(30)")
	private DeliveryTrackingStatus trackingStatus;

	@Column(name = "tracking_status_text", length = MAX_TRACKING_STATUS_TEXT_LENGTH)
	private String trackingStatusText;

	@Column(name = "last_event_at")
	private LocalDateTime lastEventAt;

	// 마지막으로 조회한 시각 (조회에 실패해도 바꿔, 한 배송이 계속 앞줄을 차지하지 않게 한다)
	@Column(name = "last_tracked_at")
	private LocalDateTime lastTrackedAt;

	// 다음에 조회할 시각 (BUK-90). 마지막 상태로 정한다(DeliveryTrackingStatus.trackingInterval). 비어 있으면 조회하지 않는다
	// (발송 전, 배송 완료, 7일 동안 조회되지 않아 멈춤)
	@Column(name = "next_tracking_at")
	private LocalDateTime nextTrackingAt;

	// 조회를 시작한 시각 (BUK-90). 발송 시각이고, 판매자가 송장을 고치면 고친 시각이다. 7일 조회 중단을 이 시각부터 센다
	@Column(name = "tracking_started_at")
	private LocalDateTime trackingStartedAt;

	@OneToMany(mappedBy = "shipment", cascade = ALL)
	private List<ShipmentItem> items = new ArrayList<>();

	// 주문 목록에서 배송마다 기록을 따로 읽지 않도록 한 번에 읽는다 (Order.shipments와 같은 방식)
	@BatchSize(size = 100)
	@OneToMany(mappedBy = "shipment", cascade = ALL, orphanRemoval = true)
	@OrderBy("eventAt asc, id asc")
	private List<ShipmentTrackingEvent> trackingEvents = new ArrayList<>();

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
	// 상대(Delivery Tracker)는 부르지 않고 아직 조회되지 않음으로 둔다. 상태는 조회 배치가 받아 온다 (BUK-55)
	void ship(DeliveryCarrier carrier, String trackingNumber, LocalDateTime shippedAt) {
		if (status != ShipmentStatus.READY) {
			throw new KitNotShippableException("이미 발송한 키트입니다.");
		}
		checkTrackingNumber(trackingNumber);
		this.status = ShipmentStatus.SHIPPING;
		this.carrierId = carrier.getId();
		this.carrier = carrier.getLabel();
		this.trackingNumber = trackingNumber.strip();
		this.shippedAt = shippedAt;
		startTracking(shippedAt);
	}

	// 판매자가 택배사·송장 번호를 고쳤다 (BUK-90). 배송 중일 때만 고친다. 옛 송장의 상태·이동 기록은 지우고 처음부터 다시 조회한다
	// 7일 조회 중단도 고친 때부터 다시 센다. 발송 시각(shippedAt)은 그대로 둔다
	void changeTracking(DeliveryCarrier carrier, String trackingNumber, LocalDateTime changedAt) {
		if (status != ShipmentStatus.SHIPPING) {
			throw new KitNotShippableException("배송 중인 키트만 송장을 고칠 수 있습니다.");
		}
		checkTrackingNumber(trackingNumber);
		// 같은 송장으로 다시 저장하면(두 번 누름) 받은 기록과 7일 기준을 그대로 둔다
		if (carrier.getId().equals(carrierId) && trackingNumber.strip().equals(this.trackingNumber)) {
			return;
		}
		this.carrierId = carrier.getId();
		this.carrier = carrier.getLabel();
		this.trackingNumber = trackingNumber.strip();
		this.trackingStatusText = null;
		this.lastEventAt = null;
		this.trackingEvents.clear();
		startTracking(changedAt);
	}

	// 아직 조회되지 않음으로 두고 다음 조회 차례에 바로 조회한다
	private void startTracking(LocalDateTime startedAt) {
		this.trackingStatus = DeliveryTrackingStatus.NOT_TRACKED;
		this.trackingStartedAt = startedAt;
		this.nextTrackingAt = startedAt;
	}

	// 조회 결과를 남긴다 (BUK-55). 아직 조회되지 않음이면 지난 상태를 그대로 두고 조회 시각만 바꾼다
	// 이동 기록은 저장하지 않은 것만 더한다. 택배사가 배송 완료라고 하면 배송 중인 키트를 배송 완료로 바꾼다 (정책 5장 3, 회의 확인 전 기본값)
	// 판매자가 먼저 배송 완료로 바꿨으면 상태·기록만 남긴다
	// 다음 조회 시각은 남긴 상태로 정하고, 7일 동안 아직 조회되지 않음이면 조회를 멈춘다 (BUK-90)
	// carrierId·trackingNumber는 조회한 송장이다. 그 사이 판매자가 송장을 고쳤거나 배송 완료로 바꿨으면 남기지 않는다
	// (배송 완료된 키트에 조회 중단 알림이 가지 않게 한다)
	TrackingOutcome applyTracking(String carrierId, String trackingNumber, DeliveryTrackingResult result,
		LocalDateTime trackedAt) {
		if (status != ShipmentStatus.SHIPPING
			|| !carrierId.equals(this.carrierId) || !trackingNumber.equals(this.trackingNumber)) {
			return TrackingOutcome.TRACKED;
		}
		this.lastTrackedAt = trackedAt;
		if (applyResult(result, trackedAt)) {
			return TrackingOutcome.DELIVERED;
		}
		if (trackingStatus == DeliveryTrackingStatus.NOT_TRACKED
			&& !trackedAt.isBefore(trackingStartedAt.plus(NOT_TRACKED_LIMIT))) {
			this.nextTrackingAt = null;
			return TrackingOutcome.STOPPED;
		}
		this.nextTrackingAt = trackedAt.plus(trackingStatus.trackingInterval());
		return TrackingOutcome.TRACKED;
	}

	// 이번에 배송 완료로 바꿨으면 true
	private boolean applyResult(DeliveryTrackingResult result, LocalDateTime trackedAt) {
		if (!result.isTracked()) {
			return false;
		}
		result.events().stream()
			.filter(event -> trackingEvents.stream().noneMatch(saved -> saved.isSameAs(event)))
			.forEach(event -> trackingEvents.add(new ShipmentTrackingEvent(this, event)));
		// 저장한 상태보다 오래된 결과(앱 두 대가 차례가 엇갈려 늦게 남김)로 상태를 되돌리지 않는다
		if (lastEventAt != null && result.lastEventAt() != null && result.lastEventAt().isBefore(lastEventAt)) {
			return false;
		}
		this.trackingStatus = result.status();
		this.trackingStatusText = ShipmentTrackingEvent.cut(result.statusText(), MAX_TRACKING_STATUS_TEXT_LENGTH);
		this.lastEventAt = result.lastEventAt();
		if (result.status() != DeliveryTrackingStatus.DELIVERED || status != ShipmentStatus.SHIPPING) {
			return false;
		}
		// 발송 하루 전보다 앞선 배송 완료는 이 키트가 아니다(송장 번호를 잘못 넣었거나 택배사가 번호를 다시 씀). 상태만 보이고 판매자가 확인한다
		// 하루는 판매자가 택배를 맡긴 뒤 늦게 발송 처리하는 몫이다
		if (result.lastEventAt() != null && result.lastEventAt().isBefore(shippedAt.minusDays(1))) {
			return false;
		}
		// 배송 완료 시각은 택배사가 남긴 배달 시각이다. 조회가 늦어져도(조회 간격, 상대 장애) 실제 시각이 남는다
		completeDelivery(result.lastEventAt() != null ? result.lastEventAt() : trackedAt);
		return true;
	}

	// 구매자 주문 상세의 배송 조회 칸 (판매자 목록은 ShipmentRepository.findSellerKits가 같은 값을 채운다)
	public KitTrackingDto toTrackingDto() {
		return KitTrackingDto.of(carrierId, trackingStatus, trackingStatusText, lastEventAt, lastTrackedAt,
			trackingEvents);
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
		this.nextTrackingAt = null;
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

	// 송장 번호가 있고 길이 안인지. 앞뒤 공백은 빼고 센다
	public static void checkTrackingNumber(String trackingNumber) {
		if (trackingNumber == null || trackingNumber.isBlank()
			|| trackingNumber.strip().length() > MAX_TRACKING_NUMBER_LENGTH) {
			throw new KitShippingInfoInvalidException(
				"송장 번호(%d자 이하)를 넣어 주세요.".formatted(MAX_TRACKING_NUMBER_LENGTH));
		}
	}
}
