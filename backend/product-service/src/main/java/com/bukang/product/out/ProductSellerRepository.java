package com.bukang.product.out;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.product.domain.ProductSeller;

public interface ProductSellerRepository extends JpaRepository<ProductSeller, Long> {
	// 회원의 승인된 판매자 (idx_product_seller_member 인덱스). memberId는 member.id로 풀린다
	// member_id는 UNIQUE가 아니지만, 회원당 승인된 판매자는 1개라고 가정한다
	Optional<ProductSeller> findByMemberIdAndStatus(long memberId, SellerStatus status);

	// 상태(승인 대기·승인·반려)와 상관없이 그 회원의 판매자 행이 있는지 (판매자 탈퇴 확인용)
	// idx_product_seller_member 인덱스의 앞 칸(member_id)을 탄다
	boolean existsByMemberId(long memberId);
}
