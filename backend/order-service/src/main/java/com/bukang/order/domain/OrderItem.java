package com.bukang.order.domain;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;
import com.bukang.order.domain.exception.OrderItemNotCancelableException;
import com.bukang.order.dto.OrderItemDto;
import com.bukang.order.out.race.RaceCourseInfo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 상품. 참가권 1장(키트 포함) = 1행이고 수량은 늘 1이다 (09 ERD order_item, 굿즈 삭제)
 * 정산(E)은 이 행의 판매자·대회·대회 날짜·금액을 읽는다 (정산 SoldEntryPort → /internal/v1/order/sold-entries?raceId=)
 * 키트 배송은 배송(Shipment)으로 옮겼다 (BUK-6). DB의 옛 kit_* 컬럼은 값을 옮긴 뒤 다음 배포에서 지운다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "ORDER_ORDER_ITEM",
	uniqueConstraints = {
		// 결제 1건 = 참가 신청 1건 (ERD 추천안 v4 연결점). 같은 신청서로 주문이 두 번 생기지 않는다
		@UniqueConstraint(name = "uk_order_item_participation", columnNames = {"participation_id"})
	},
	indexes = {
		// 정산 배치가 대회 날짜로 결제된 참가권을 찾는다 (정산용 판매 내역 조회)
		// 같음 조건(status = PAID)을 앞에, 범위 조건(race_date)을 뒤에 둬야 두 조건을 모두 인덱스로 좁힌다
		// 이름을 바꾼 건 dev(ddl-auto: update)가 같은 이름의 옛 인덱스를 고치지 않아서다
		@Index(name = "idx_order_item_status_race_date", columnList = "status, race_date")
	}
)
public class OrderItem extends BaseIdAndTime {
	private static final int TICKET_QUANTITY = 1;

	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	@Column(name = "participation_id", nullable = false)
	private long participationId;

	@Column(name = "race_id", nullable = false)
	private long raceId;

	@Column(name = "race_course_id", nullable = false)
	private long raceCourseId;

	@Column(name = "seller_id", nullable = false)
	private long sellerId;

	// 대회 일시 사본 (주문 당시 종목 정보). 정산 배치가 대회 날짜로 판매 내역을 찾는 조건이다
	@Column(name = "race_date", nullable = false)
	private LocalDateTime raceDate;

	// 주문 당시 상품명·단가. 판매자가 나중에 고쳐도 주문 내역은 바뀌지 않는다
	@Column(name = "item_name", nullable = false, length = 200)
	private String itemName;

	@Column(name = "unit_price", nullable = false)
	private long unitPrice;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	// 정산 판매액 = 실제 결제 금액 (정책 6장 1-5)
	@Column(name = "amount", nullable = false)
	private long amount;

	@Enumerated(STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	private OrderItemStatus status;

	@Column(name = "canceled_amount", nullable = false)
	private long canceledAmount;

	@Column(name = "canceled_at")
	private LocalDateTime canceledAt;

	// 취소를 시작한 시각 (BUK-9). 환불 결과를 모르는 취소 중을 재처리가 이 시각으로 고른다. 결제 완료로 되돌리면 지운다
	@Column(name = "cancel_requested_at")
	private LocalDateTime cancelRequestedAt;

	static OrderItem ticket(Order order, Participation participation, RaceCourseInfo course) {
		OrderItem item = new OrderItem();
		item.order = order;
		item.participationId = participation.getId();
		item.raceId = course.raceId();
		item.raceCourseId = course.raceCourseId();
		item.sellerId = course.sellerId();
		item.raceDate = course.raceDate();
		item.itemName = course.courseName();
		item.unitPrice = course.price();
		item.quantity = TICKET_QUANTITY;
		item.amount = item.unitPrice * TICKET_QUANTITY;
		item.status = OrderItemStatus.ORDERED;
		return item;
	}

	void markPaid() {
		this.status = OrderItemStatus.PAID;
	}

	// 결제한 상품만 취소할 수 있다. 같은 취소가 두 번 오면 두 번째는 여기서 막혀 환불도 한 번만 일어난다
	// 취소 중이면 환불 결과를 기다리는 중이라 다시 시작하지 않는다. 재처리가 마무리한다 (BUK-9)
	public void checkCancelable() {
		if (status == OrderItemStatus.CANCELED) {
			throw new OrderItemNotCancelableException("이미 취소한 상품입니다.");
		}
		if (status == OrderItemStatus.CANCELING) {
			throw new OrderItemNotCancelableException("취소 처리 중인 상품입니다. 잠시 뒤 주문 내역에서 확인해주세요.");
		}
		if (status != OrderItemStatus.PAID) {
			throw new OrderItemNotCancelableException(
				"결제한 상품만 취소할 수 있습니다(현재 상태: %s).".formatted(status.getLabel()));
		}
	}

	// 사용자 취소의 첫 단계: 환불을 부르기 전에 취소 중과 시작 시각을 커밋한다 (결제 완료 → 취소 중)
	void startCancel(LocalDateTime requestedAt) {
		checkCancelable();
		this.status = OrderItemStatus.CANCELING;
		this.cancelRequestedAt = requestedAt;
	}

	public boolean isCanceling() {
		return status == OrderItemStatus.CANCELING;
	}

	// 취소 중에서 마무리하거나(사용자 취소, BUK-9) 결제 완료에서 바로 취소한다(대회 취소 일괄 환불)
	void cancel(LocalDateTime canceledAt) {
		if (status != OrderItemStatus.CANCELING) {
			checkCancelable();
		}
		this.status = OrderItemStatus.CANCELED;
		this.canceledAmount = amount;
		this.canceledAt = canceledAt;
	}

	// 예치금이 환불을 거절했다(4xx). 돈이 움직이지 않았으므로 결제 완료로 되돌리고 시작 시각을 지운다 (취소 중 → 결제 완료)
	void revertCancel() {
		if (status != OrderItemStatus.CANCELING) {
			throw new IllegalStateException(
				"취소 중인 상품만 되돌릴 수 있습니다(현재 상태: %s).".formatted(status.getLabel()));
		}
		this.status = OrderItemStatus.PAID;
		this.cancelRequestedAt = null;
	}

	public boolean isSoldBy(long sellerId) {
		return this.sellerId == sellerId;
	}

	// 키트 배송 칸은 배송(Shipment)에서 채운다. 배송이 아직 없으면 kitDeliveryStatus만 부르는 쪽이 정한다 (Order.toDto)
	OrderItemDto toDto(KitDeliveryStatus kitDeliveryStatus, Shipment shipment) {
		if (shipment == null) {
			return new OrderItemDto(getId(), participationId, raceCourseId, sellerId, itemName, unitPrice, quantity,
				amount, status, kitDeliveryStatus, null, null, null, null);
		}
		return new OrderItemDto(getId(), participationId, raceCourseId, sellerId, itemName, unitPrice, quantity,
			amount, status, shipment.getStatus().getKitDeliveryStatus(), shipment.getCarrier(),
			shipment.getTrackingNumber(), shipment.getShippedAt(), shipment.getDeliveredAt());
	}
}
