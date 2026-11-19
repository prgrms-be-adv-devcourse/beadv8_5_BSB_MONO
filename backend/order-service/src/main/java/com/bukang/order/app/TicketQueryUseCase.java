package com.bukang.order.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.order.domain.exception.OrderItemNotFoundException;
import com.bukang.order.dto.TicketDto;
import com.bukang.order.out.OrderItemRepository;

import lombok.RequiredArgsConstructor;

/**
 * 회원 서비스가 기록증 제출을 받을 때 참가권 주인·상태를 확인한다 (BUK-271)
 * 대회·종목 이름은 필요 없어 상품 서비스는 부르지 않는다
 */
@Service
@RequiredArgsConstructor
public class TicketQueryUseCase {
	private final OrderItemRepository orderItemRepository;

	@Transactional(readOnly = true)
	public TicketDto findTicket(long orderItemId) {
		return orderItemRepository.findTicket(orderItemId)
			.orElseThrow(() -> new OrderItemNotFoundException("존재하지 않는 참가권입니다."));
	}
}
