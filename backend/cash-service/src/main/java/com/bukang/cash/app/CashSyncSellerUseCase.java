package com.bukang.cash.app;

import org.springframework.stereotype.Service;

import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.seller.CashSeller;
import com.bukang.cash.out.CashMemberRepository;
import com.bukang.cash.out.CashSellerRepository;
import com.bukang.common.shared.seller.event.SellerChangedEvent;

import lombok.RequiredArgsConstructor;

/**
 * 판매자 변경 이벤트로 CashSeller(판매자 신청서 ID → 회원) 복제본을 만든다 (PRO-256)
 * 등록 신청·승인·반려 모두 원본 값으로 덮어쓴다. 신청서 ID → 회원은 신청·승인·반려에서 바뀌지 않아 판매자 메시지끼리의 순서는 따지지 않는다.
 */
@Service
@RequiredArgsConstructor
public class CashSyncSellerUseCase {
	private final CashSellerRepository cashSellerRepository;
	private final CashMemberRepository cashMemberRepository;

	public CashSeller syncSeller(SellerChangedEvent event) {
		CashMember member = cashMemberRepository.findById(event.memberId())
			.orElseThrow(() -> new IllegalStateException(
				"회원 복제본이 아직 없어 판매자 메시지를 다시 시도합니다. sellerId=%d, memberId=%d"
					.formatted(event.id(), event.memberId())));
		return cashSellerRepository.save(new CashSeller(event.id(), member));
	}
}
