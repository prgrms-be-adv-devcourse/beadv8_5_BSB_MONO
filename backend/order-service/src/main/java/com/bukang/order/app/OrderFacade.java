package com.bukang.order.app;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

	// 상품 HTTP(종목 조회·자리 확보)를 잠금 밖에서 부르려고 트랜잭션은 OrderCreateUseCase가 단계마다 연다 (BUK-213)
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
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

	// 상품 HTTP를 잠금 밖에서 부르려고 트랜잭션은 OrderPayUseCase가 연다
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public OrderPayResult pay(long orderId, long memberId, List<Long> kitOptionIds) {
		return orderPayUseCase.pay(memberId, orderId, kitOptionIds, LocalDateTime.now());
	}

	// 다른 서비스에 묻는 판정을 잠금 밖에서 하려고 트랜잭션은 OrderCancelUseCase가 단계마다 연다
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public OrderCancelUseCase.CancelResult cancelItem(long orderId, long orderItemId, long memberId) {
		return orderCancelUseCase.cancel(memberId, orderId, orderItemId, LocalDateTime.now());
	}
}
