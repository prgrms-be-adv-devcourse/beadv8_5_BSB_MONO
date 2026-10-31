package com.bukang.payout.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.dto.SellerReceivableDto;
import com.bukang.payout.out.PayoutItemRepository;

import lombok.RequiredArgsConstructor;

/**
 * 판매자에게 갚을 돈이 남았는지 원장 잔고로 본다 (선 환불 후 회수 R11, 탈퇴 제한)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayoutReceivableQueryUseCase {
	private final PayoutItemRepository payoutItemRepository;

	public SellerReceivableDto findReceivable(long sellerId) {
		return SellerReceivableDto.of(sellerId, payoutItemRepository.findRaceIdsWithReceivable(sellerId));
	}
}
