package com.bukang.order.app;

import java.time.LocalDate;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.bukang.order.domain.Order;
import com.bukang.order.domain.ShippingAddress;
import com.bukang.order.domain.exception.OrderAccessDeniedException;
import com.bukang.order.domain.exception.OrderNotFoundException;
import com.bukang.order.domain.exception.OrderSearchPeriodException;
import com.bukang.order.dto.OrderDto;
import com.bukang.order.dto.OrderPageDto;
import com.bukang.order.out.OrderQueryRepository;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationRepository;

import lombok.RequiredArgsConstructor;

/**
 * 내 주문 목록·상세 조회 (PRO-43). 상품명·단가는 주문 당시 값이라 상품 서비스(B)를 다시 부르지 않는다
 */
@Service
@RequiredArgsConstructor
public class OrderQueryUseCase {
	private final OrderRepository orderRepository;
	private final OrderQueryRepository orderQueryRepository;
	private final ParticipationRepository participationRepository;

	public OrderPageDto findMine(long memberId, LocalDate from, LocalDate to, int page, int size) {
		if (from != null && to != null && from.isAfter(to)) {
			throw new OrderSearchPeriodException("조회 시작일이 종료일보다 늦습니다.");
		}
		return OrderPageDto.from(orderQueryRepository.findMine(memberId, from, to, PageRequest.of(page, size))
			.map(Order::toSummaryDto));
	}

	public OrderDto findOwned(long memberId, long orderId) {
		Order order = orderRepository.findById(orderId)
			.orElseThrow(() -> new OrderNotFoundException("존재하지 않는 주문입니다."));
		if (!order.isOwnedBy(memberId)) {
			throw new OrderAccessDeniedException("본인의 주문만 볼 수 있습니다.");
		}
		if (!order.getShipments().isEmpty()) {
			return order.toDto();
		}
		// 결제 전이라 배송이 아직 없으면 참가 신청의 배송지를 보여 준다 (BUK-6)
		ShippingAddress applicationShippingAddress = participationRepository.findById(order.participationId())
			.map(participation -> participation.getParticipant().getShippingAddress())
			.orElse(null);
		return order.toDto(applicationShippingAddress);
	}
}
