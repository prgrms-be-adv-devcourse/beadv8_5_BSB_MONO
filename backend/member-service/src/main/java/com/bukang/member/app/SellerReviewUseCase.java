package com.bukang.member.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.bukang.member.domain.Seller;
import com.bukang.member.domain.exception.SellerNotFoundException;
import com.bukang.member.out.SellerRepository;

import lombok.RequiredArgsConstructor;

// 관리자의 판매자 신청 승인·반려 (BE-020). 관리자 확인은 Facade가 먼저 한다
// 상태 확인과 이벤트 발행은 엔티티(Seller.approve·reject)가 하고, 바뀐 값은 커밋 때 반영된다
@Service
@RequiredArgsConstructor
public class SellerReviewUseCase {
	private final SellerRepository sellerRepository;

	public Seller approve(long adminId, long sellerId, LocalDateTime now) {
		Seller seller = findSeller(sellerId);
		seller.approve(adminId, now);
		return seller;
	}

	public Seller reject(long adminId, long sellerId, String reason, LocalDateTime now) {
		Seller seller = findSeller(sellerId);
		seller.reject(adminId, reason, now);
		return seller;
	}

	// 줄을 잠그고 읽어, 같은 신청을 동시에 심사하면 뒤 요청이 409(이미 심사함)가 되게 한다
	private Seller findSeller(long sellerId) {
		return sellerRepository.findByIdForUpdate(sellerId)
			.orElseThrow(() -> new SellerNotFoundException("존재하지 않는 판매자 신청입니다."));
	}
}
