package com.bukang.order.domain;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 배송 상자에 든 주문 상품 (BUK-6). 지금은 참가권 1장에 키트 1개라 수량은 늘 1이다
 * 같은 상품을 다시 보내면(재발송) 새 배송에 새 줄이 생긴다. 한 배송 안에서는 같은 상품이 한 줄뿐이다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "ORDER_SHIPMENT_ITEM",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_shipment_item", columnNames = {"shipment_id", "order_item_id"})
	}
)
public class ShipmentItem extends BaseIdAndTime {
	private static final int KIT_QUANTITY = 1;

	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "shipment_id", nullable = false)
	private Shipment shipment;

	// 같은 주문의 주문 상품 ID. 주문 상품의 참가 신청 ID처럼 연관 없이 값만 둔다
	@Column(name = "order_item_id", nullable = false)
	private long orderItemId;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	ShipmentItem(Shipment shipment, OrderItem orderItem) {
		this.shipment = shipment;
		this.orderItemId = orderItem.getId();
		this.quantity = KIT_QUANTITY;
	}
}
