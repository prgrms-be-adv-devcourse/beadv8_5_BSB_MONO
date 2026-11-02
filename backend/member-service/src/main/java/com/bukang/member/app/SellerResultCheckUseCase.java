package com.bukang.member.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.bukang.member.domain.Seller;
import com.bukang.member.domain.exception.SellerNotFoundException;
import com.bukang.member.out.SellerRepository;

import lombok.RequiredArgsConstructor;

// 신청자가 가장 최근 판매자 신청의 심사 결과를 봤다고 남긴다. 결과 화면을 한 번만 보여 주는 데 쓴다
// 바뀐 값은 커밋 때 반영된다
@Service
@RequiredArgsConstructor
public class SellerResultCheckUseCase {
	private final SellerRepository sellerRepository;

	public Seller check(long memberId, LocalDateTime now) {
		Seller seller = sellerRepository.findLatestByMemberIdForUpdate(memberId)
			.orElseThrow(() -> new SellerNotFoundException("판매자 신청 내역이 없습니다."));
		seller.checkResult(now);
		return seller;
	}
}
