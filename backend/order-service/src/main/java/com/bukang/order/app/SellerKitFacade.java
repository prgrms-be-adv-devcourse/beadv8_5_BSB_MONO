package com.bukang.order.app;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.order.domain.DeliveryCarrier;
import com.bukang.order.domain.KitDeliveryStatus;
import com.bukang.order.domain.exception.SellerAccessDeniedException;
import com.bukang.order.dto.DeliveryCarrierDto;
import com.bukang.order.dto.KitShipmentRequestDto;
import com.bukang.order.dto.OrderItemDto;
import com.bukang.order.dto.SellerKitPageDto;
import com.bukang.order.dto.SellerKitSummaryDto;
import com.bukang.order.out.seller.SellerLookupPort;

import lombok.RequiredArgsConstructor;

/**
 * 판매자 키트 배송 API의 입구 (PRO-197). 회원 ID로 승인된 판매자 ID를 찾은 뒤 키트 배송 유스케이스에 넘긴다
 * 판매자 ID 조회와 상품 종목 조회가 다른 서비스를 부를 수 있어 바깥 트랜잭션 없이 부르고,
 * 트랜잭션은 KitDeliveryUseCase가 연다
 */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@RequiredArgsConstructor
public class SellerKitFacade {
	static final String NOT_SELLER_MESSAGE = "승인된 판매자만 쓸 수 있습니다.";

	private final SellerLookupPort sellerLookupPort;
	private final KitDeliveryUseCase kitDeliveryUseCase;

	public SellerKitPageDto findKits(long memberId, Long raceId, KitDeliveryStatus status, int page, int size) {
		return kitDeliveryUseCase.findKits(sellerIdOf(memberId), raceId, status, page, size);
	}

	public SellerKitSummaryDto summarize(long memberId, Long raceId) {
		return kitDeliveryUseCase.summarize(sellerIdOf(memberId), raceId, LocalDateTime.now());
	}

	public OrderItemDto ship(long memberId, long orderItemId, KitShipmentRequestDto request) {
		return kitDeliveryUseCase.ship(sellerIdOf(memberId), orderItemId, carrierIdOf(request),
			request.getTrackingNumber(), LocalDateTime.now());
	}

	// carrierId가 없으면 옛 칸(택배사 이름)으로 찾는다. 목록에 없는 이름은 그대로 넘겨 400(목록에 없는 택배사)이 된다
	private static String carrierIdOf(KitShipmentRequestDto request) {
		if (request.getCarrierId() != null && !request.getCarrierId().isBlank()) {
			return request.getCarrierId();
		}
		return DeliveryCarrier.findByLabel(request.getCarrier())
			.map(DeliveryCarrier::getId)
			.orElse(request.getCarrier());
	}

	public List<DeliveryCarrierDto> findCarriers() {
		return kitDeliveryUseCase.findCarriers();
	}

	public OrderItemDto changeTracking(long memberId, long orderItemId, KitShipmentRequestDto request) {
		return kitDeliveryUseCase.changeTracking(sellerIdOf(memberId), orderItemId, carrierIdOf(request),
			request.getTrackingNumber(), LocalDateTime.now());
	}

	public OrderItemDto completeDelivery(long memberId, long orderItemId) {
		return kitDeliveryUseCase.completeDelivery(sellerIdOf(memberId), orderItemId, LocalDateTime.now());
	}

	private long sellerIdOf(long memberId) {
		return sellerLookupPort.findApprovedSellerId(memberId)
			.orElseThrow(() -> new SellerAccessDeniedException(NOT_SELLER_MESSAGE));
	}
}
