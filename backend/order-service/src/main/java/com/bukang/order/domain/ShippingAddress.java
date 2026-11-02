package com.bukang.order.domain;

import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 키트 배송지 (ERD v5 orders.recipient_name ~ address_detail, delivery와 같은 칸)
 * 정책상 신청 때 받아(3장 1-2, 5장 3-2) 참가 신청에 두고, 결제할 때 배송(ORDER_SHIPMENT)에 복사한다 (BUK-6)
 * 컬럼 이름은 배송 기준이고, 참가 신청에서는 shipping_ 접두사를 붙여 쓴다 (ParticipantInfo의 @AttributeOverrides)
 */
@Embeddable
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor
@Getter
public class ShippingAddress {
	@Column(name = "recipient_name", length = 50)
	private String recipientName;

	@Column(name = "recipient_phone", length = 20)
	private String recipientPhone;

	@Column(name = "zipcode", length = 10)
	private String zipcode;

	@Column(name = "address", length = 255)
	private String address;

	// 상세 주소. 없을 수 있다
	@Column(name = "address_detail", length = 255)
	private String addressDetail;

	// 참가 신청과 주문이 같은 객체를 나눠 쓰지 않도록 값을 복사한다. 원본이 없으면 null
	public static ShippingAddress copyOf(ShippingAddress source) {
		if (source == null) {
			return null;
		}
		return new ShippingAddress(source.recipientName, source.recipientPhone, source.zipcode, source.address,
			source.addressDetail);
	}
}
