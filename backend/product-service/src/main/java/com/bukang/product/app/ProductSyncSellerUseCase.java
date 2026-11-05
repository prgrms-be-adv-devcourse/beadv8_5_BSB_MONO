package com.bukang.product.app;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.product.domain.ProductMember;
import com.bukang.product.domain.ProductSeller;
import com.bukang.product.out.ProductMemberRepository;
import com.bukang.product.out.ProductSellerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSyncSellerUseCase {
	private final ProductSellerRepository productSellerRepository;
	private final ProductMemberRepository productMemberRepository;

	// 원본 값으로 덮어쓴다 (등록 신청, 승인·반려 모두 같은 방식). 같은 메시지가 다시 와도 결과가 같다
	public Optional<ProductSeller> syncSeller(long id, long memberId, SellerType sellerType, String businessName,
		String businessNumber, String representativeName, SellerStatus status, LocalDateTime requestedAt,
		LocalDateTime reviewedAt) {
		// 회원가입을 해야 판매자 등록을 할 수 있으므로, 회원 복제본이 없는 판매자 메시지는 잘못된 메시지다
		// 다시 시도하지 않고 에러 로그만 남긴 뒤 건너뛴다
		if (!productMemberRepository.existsById(memberId)) {
			log.error("회원 복제본이 없는 판매자 메시지를 건너뜁니다. sellerId={}, memberId={}", id, memberId);
			return Optional.empty();
		}

		ProductMember member = productMemberRepository.getReferenceById(memberId);
		return Optional.of(productSellerRepository.save(
			new ProductSeller(
				id,
				member,
				sellerType,
				businessName,
				businessNumber,
				representativeName,
				status,
				requestedAt,
				reviewedAt
			)
		));
	}
}
