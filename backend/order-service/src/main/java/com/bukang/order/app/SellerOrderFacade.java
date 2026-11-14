package com.bukang.order.app;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.order.domain.OrderItemStatus;
import com.bukang.order.domain.exception.SellerAccessDeniedException;
import com.bukang.order.dto.SellerOrderPageDto;
import com.bukang.order.dto.SellerPartyPageDto;
import com.bukang.order.out.seller.SellerLookupPort;

import lombok.RequiredArgsConstructor;

/**
 * 판매자 주문 API의 입구 (BUK-227). 회원 ID로 승인된 판매자 ID를 찾은 뒤 조회 유스케이스에 넘긴다 (SellerKitFacade와 같은 방식)
 * 판매자 확인·상품 종목 조회가 다른 서비스를 부를 수 있어 바깥 트랜잭션 없이 부른다
 */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@RequiredArgsConstructor
public class SellerOrderFacade {
	private final SellerLookupPort sellerLookupPort;
	private final SellerOrderQueryUseCase sellerOrderQueryUseCase;

	public SellerOrderPageDto findOrders(long memberId, Long raceId, Long raceCourseId, OrderItemStatus status,
		Long partyId, LocalDate from, LocalDate to, int page, int size) {
		return sellerOrderQueryUseCase.findOrders(sellerIdOf(memberId), raceId, raceCourseId, status, partyId, from,
			to, page, size);
	}

	public SellerPartyPageDto findParties(long memberId, long raceId, Long partyId, LocalDate from, LocalDate to,
		int page, int size) {
		return sellerOrderQueryUseCase.findParties(sellerIdOf(memberId), raceId, partyId, from, to, page, size);
	}

	private long sellerIdOf(long memberId) {
		return sellerLookupPort.findApprovedSellerId(memberId)
			.orElseThrow(() -> new SellerAccessDeniedException(SellerKitFacade.NOT_SELLER_MESSAGE));
	}
}
