package com.bukang.product.domain;

import java.time.LocalDateTime;

import com.bukang.common.shared.member.domain.ReplicaMember;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 서비스가 회원 이벤트(MemberJoinedEvent)로 복제해 보관하는 회원 (구매자)
 * ID와 생성/수정 시각은 원본 회원(member-service) 값을 그대로 쓴다.
 */
@Entity
@NoArgsConstructor
@Getter
@Table(name = "PRODUCT_MEMBER")
public class ProductMember extends ReplicaMember {
	public ProductMember(
		long id,
		LocalDateTime createDate,
		LocalDateTime modifyDate,
		String nickname
	) {
		super(id, createDate, modifyDate, nickname);
	}
}
