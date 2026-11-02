package com.bukang.order.app;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.order.dto.ActiveTicketCountDto;
import com.bukang.order.out.OrderItemRepository;

import lombok.RequiredArgsConstructor;

/**
 * 회원 서비스가 탈퇴 전에 부르는 끝나지 않은 참가권 수 조회 (BE-022)
 * 끝나지 않은 참가권 = 결제 완료이고 취소·환불되지 않았으며, 대회 날짜가 오늘 이후(오늘 포함)인 참가권
 * 대회 날짜는 주문 때 복사해 둔 주문 상품의 race_date로 본다 (상품 서비스를 부르지 않는다)
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ActiveTicketQueryUseCase {
	private final OrderItemRepository orderItemRepository;

	public ActiveTicketCountDto countActiveTickets(long memberId, LocalDate today, LocalDateTime now) {
		long count = orderItemRepository.countActiveTickets(memberId, today.atStartOfDay(), now);
		return new ActiveTicketCountDto(Math.toIntExact(count));
	}
}
