package com.bukang.order.domain;

import static lombok.AccessLevel.PROTECTED;

import com.bukang.common.global.jpa.entity.BaseManualIdAndTime;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 키트 옵션별 판매 수 (11/07·11/09 결정). ID는 상품의 옵션 ID다
 * 상품은 준비 수량(stock)만 알고, 몇 개 팔렸는지는 주문이 센다. 결제 때 sold < stock이면 +1, 결제한 참가권이 취소되면 -1
 * 판매 수는 줄을 잠근 뒤(KitOptionSaleRepository.findByIdForUpdate)에만 바꾼다. 잠그지 않고 고치면 동시 결제가 서로 덮는다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "ORDER_KIT_OPTION_SALE",
	indexes = @Index(name = "idx_kit_option_sale_race", columnList = "race_id")
)
@AttributeOverride(name = "id", column = @Column(name = "kit_option_id"))
public class KitOptionSale extends BaseManualIdAndTime {
	@Column(name = "race_id", nullable = false)
	private long raceId;

	@Column(name = "sold", nullable = false)
	private int sold;

	// 준비 수량(null이면 맞춤 제작이라 늘 된다) 안이면 판매 수를 1 늘리고 true, 품절이면 false
	public boolean increase(Integer stock) {
		if (stock != null && sold >= stock) {
			return false;
		}
		sold++;
		return true;
	}

	// 결제한 참가권이 취소됐다. 0 아래로는 내려가지 않는다
	public void decrease() {
		if (sold > 0) {
			sold--;
		}
	}
}
