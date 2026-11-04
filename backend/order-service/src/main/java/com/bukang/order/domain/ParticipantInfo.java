package com.bukang.order.domain;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDate;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 참가 신청서에 적는 참가자 정보 (정책 3장 1-2)
 * 접수 마감 전까지 통째로 바꿀 수 있고, 다음 신청 때 그대로 채워 준다
 * 컬럼은 파티원 행 때문에 NULL을 허용하고(ERD v4), 개인 신청의 필수 여부는 요청 DTO에서 검사한다
 */
@Embeddable
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor
@Getter
public class ParticipantInfo {
	@Column(name = "participant_name", length = 50)
	private String name;

	@Column(name = "participant_birth")
	private LocalDate birth;

	@Enumerated(STRING)
	@Column(name = "participant_gender", columnDefinition = "varchar(10)")
	private Gender gender;

	@Column(name = "participant_phone", length = 20)
	private String phone;

	@Column(name = "emergency_contact_name", length = 50)
	private String emergencyContactName;

	@Column(name = "emergency_contact_phone", length = 20)
	private String emergencyContactPhone;

	// 기념 티셔츠(키트) 사이즈. 종목마다 고를 수 있는 값이 달라 문자열로 둔다 (ERD v4 tshirt_size)
	@Column(name = "tshirt_size", length = 10)
	private String tshirtSize;

	// 키트 배송지. 키트가 있는 종목만 받는다 (정책 3장 1-2 "키트를 배송하는 대회면 배송 주소도 받는다", PRO-123)
	// 주문을 만들 때 주문(orders)의 배송지 칸으로 옮긴다
	@Embedded
	@AttributeOverrides({
		@AttributeOverride(name = "recipientName", column = @Column(name = "shipping_recipient_name", length = 50)),
		@AttributeOverride(name = "recipientPhone", column = @Column(name = "shipping_recipient_phone", length = 20)),
		@AttributeOverride(name = "zipcode", column = @Column(name = "shipping_zipcode", length = 10)),
		@AttributeOverride(name = "address", column = @Column(name = "shipping_address", length = 255)),
		@AttributeOverride(name = "addressDetail", column = @Column(name = "shipping_address_detail", length = 255))
	})
	private ShippingAddress shippingAddress;

	// 배송지만 바꾼 참가자 정보. 키트가 없는 종목은 null을 넣어 배송지를 남기지 않는다
	public ParticipantInfo withShippingAddress(ShippingAddress newShippingAddress) {
		return new ParticipantInfo(name, birth, gender, phone, emergencyContactName, emergencyContactPhone, tshirtSize,
			newShippingAddress);
	}

	// 티셔츠 사이즈만 바꾼 참가자 정보. 티셔츠가 없는 종목은 null을 넣어 사이즈를 남기지 않는다
	public ParticipantInfo withTshirtSize(String newTshirtSize) {
		return new ParticipantInfo(name, birth, gender, phone, emergencyContactName, emergencyContactPhone,
			newTshirtSize, shippingAddress);
	}
}
