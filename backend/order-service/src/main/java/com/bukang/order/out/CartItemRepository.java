package com.bukang.order.out;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.order.domain.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
	/**
	 * 장바구니 결제로 주문한 종목을 장바구니에서 뺀다 (PRO-45)
	 * 엔티티로 지우면 회원이 같은 항목을 동시에 지웠을 때 0행 삭제 오류로 주문까지 롤백되므로, 조건부 DELETE로 지운다.
	 * 이미 없으면 0을 돌려줄 뿐이다
	 */
	// 장바구니와 종목으로 지워 유니크 인덱스(uk_cart_item_course)만 타고, 그 한 행만 잠근다
	@Modifying(flushAutomatically = true)
	@Query("delete from CartItem i where i.cart.id = :cartId and i.raceCourseId = :raceCourseId")
	int deleteByCartIdAndRaceCourseId(@Param("cartId") long cartId, @Param("raceCourseId") long raceCourseId);
}
