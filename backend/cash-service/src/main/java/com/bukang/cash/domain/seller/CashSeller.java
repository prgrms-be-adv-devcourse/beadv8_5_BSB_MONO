package com.bukang.cash.domain.seller;

import static jakarta.persistence.FetchType.LAZY;

import com.bukang.cash.domain.member.CashMember;
import com.bukang.common.global.jpa.entity.BaseManualIdAndTime;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매자 등록 신청(member-service Seller)의 cash-service 복제본 (PRO-256 리뷰 반영)
 * id는 판매자 신청서 ID(= SellerChangedEvent.id)다. 회원 ID(= 지갑 ID)와 다른 값이라,
 * 정산 지급 때 받는 sellerId(신청서 ID)로 실제 지갑 주인(회원)을 찾으려면 이 복제본을 거쳐야 한다.
 * SellerChangedEvent(신청, 승인·반려)를 받을 때마다 원본 값으로 덮어쓴다 (CashMember와 같은 방식).
 */
@Entity
@NoArgsConstructor
@Getter
@Table(name = "CASH_SELLER")
public class CashSeller extends BaseManualIdAndTime {
	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "member_id")
	private CashMember member;

	public CashSeller(long id, CashMember member) {
		super(id);
		this.member = member;
	}
}
