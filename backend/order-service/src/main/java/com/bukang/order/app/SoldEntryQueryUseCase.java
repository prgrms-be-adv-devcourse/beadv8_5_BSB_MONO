package com.bukang.order.app;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.order.dto.SoldEntryDto;
import com.bukang.order.out.OrderItemRepository;

import lombok.RequiredArgsConstructor;

/**
 * 정산 서비스(E)가 최종 정산 생성 배치에서 부르는 판매 내역 조회 (payout SoldEntryPort.findSoldEntries)
 * 대회의 결제 완료 · 미취소 참가권을 돌려준다. 정산할 대회는 정산 서비스가 상품 서비스의 대회일로 정하고 대회 ID로 묻는다
 * 대회가 있는지는 주문 서비스가 모르므로, 판매가 없는 대회와 없는 대회 모두 빈 목록이다
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SoldEntryQueryUseCase {
	private final OrderItemRepository orderItemRepository;

	public List<SoldEntryDto> findSoldEntries(long raceId) {
		return orderItemRepository.findSoldEntries(raceId);
	}
}
