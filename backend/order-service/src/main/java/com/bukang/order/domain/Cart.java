package com.bukang.order.domain;

import static jakarta.persistence.CascadeType.ALL;
import static lombok.AccessLevel.PROTECTED;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;
import com.bukang.order.domain.exception.CartCourseLimitException;
import com.bukang.order.dto.CartDto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장바구니. 회원별로 서버에 1개 저장해 어느 기기에서든 같은 장바구니를 본다 (정책 4장 1-1, ERD 최신 통합본 cart)
 * 담기만으로는 자리를 확보하지 않는다. 자리는 [결제하기] 때 상품 서비스(B)가 확보한다 (PRO-41)
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "ORDER_CART",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_cart_member", columnNames = {"member_id"})
	}
)
public class Cart extends BaseIdAndTime {
	@Column(name = "member_id", nullable = false)
	private long memberId;

	// 최근에 담은 항목이 먼저 보인다 (한 종목만 담으므로 지금은 하나뿐이다. 예전에 여러 개 담긴 장바구니를 위해 둔다)
	@OneToMany(mappedBy = "cart", cascade = ALL, orphanRemoval = true)
	@OrderBy("id DESC")
	private List<CartItem> items = new ArrayList<>();

	private Cart(long memberId) {
		this.memberId = memberId;
	}

	public static Cart of(long memberId) {
		return new Cart(memberId);
	}

	public boolean isOwnedBy(long targetMemberId) {
		return memberId == targetMemberId;
	}

	public Optional<CartItem> findItem(long raceCourseId) {
		return items.stream()
			.filter(item -> item.getRaceCourseId() == raceCourseId)
			.findFirst();
	}

	// 참가권은 수량 1로 고정이라, 이미 담긴 종목이면 새 항목을 만들지 않고 그 항목을 돌려준다 (정책 4장 1-1)
	// 장바구니에는 한 종목만 담는다. 개인 예매는 모두 대기열을 거치는데, 여러 종목이면 여러 대기열에 동시에 입장해야 한다 (PRO-192)
	public CartItem addItem(long raceCourseId) {
		return findItem(raceCourseId).orElseGet(() -> {
			if (!items.isEmpty()) {
				throw new CartCourseLimitException("장바구니에는 한 종목만 담을 수 있습니다. 담긴 종목을 뺀 뒤 담아주세요.");
			}
			CartItem item = new CartItem(this, raceCourseId);
			items.add(item);
			return item;
		});
	}

	public void removeItem(CartItem item) {
		items.remove(item);
	}

	// pendingByCourse는 종목 ID → 그 종목으로 저장한 결제 전 신청서
	public CartDto toDto(Map<Long, Participation> pendingByCourse) {
		return new CartDto(memberId,
			items.stream().map(item -> item.toDto(pendingByCourse.get(item.getRaceCourseId()))).toList());
	}
}
