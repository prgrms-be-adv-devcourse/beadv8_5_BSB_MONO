package com.bukang.order.app;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.order.dto.OrderCreateRequestDto;
import com.bukang.order.dto.OrderDto;
import com.bukang.order.dto.OrderPageDto;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderFacade {
	private final OrderCreateUseCase orderCreateUseCase;
	private final OrderQueryUseCase orderQueryUseCase;
	private final OrderPayUseCase orderPayUseCase;
	private final OrderCancelUseCase orderCancelUseCase;

	@Transactional
	public OrderCreateResult create(OrderCreateRequestDto request, String idempotencyKey, long memberId) {
		return orderCreateUseCase.create(memberId, request.getParticipationId(), idempotencyKey,
			LocalDateTime.now());
	}

	@Transactional
	public OrderPageDto findMine(long memberId, LocalDate from, LocalDate to, int page, int size) {
		return orderQueryUseCase.findMine(memberId, from, to, page, size);
	}

	public OrderDto findById(long orderId, long memberId) {
		return orderQueryUseCase.findOwned(memberId, orderId);
	}

	@Transactional
	public OrderPayResult pay(long orderId, long memberId) {
		return orderPayUseCase.pay(memberId, orderId, LocalDateTime.now());
	}

	// 다른 서비스에 묻는 판정을 잠금 밖에서 하려고 트랜잭션은 OrderCancelUseCase가 단계마다 연다
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public OrderCancelUseCase.CancelResult cancelItem(long orderId, long orderItemId, long memberId) {
		return orderCancelUseCase.cancel(memberId, orderId, orderItemId, LocalDateTime.now());
	}
}
