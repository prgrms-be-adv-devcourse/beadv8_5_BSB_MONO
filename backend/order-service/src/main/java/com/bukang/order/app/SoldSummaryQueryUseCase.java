package com.bukang.order.app;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.order.dto.SoldSummaryDto;
import com.bukang.order.out.OrderItemRepository;

import lombok.RequiredArgsConstructor;

/**
 * 정산 서비스(E)가 1차 정산 생성 배치에서 부르는 판매 집계 (payout SoldSummaryPort.summarizeSold, PRO-231)
 * 대회의 결제 완료 · 미취소 참가권을 판매자 × 코스로 묶어 가격 합과 장수를 돌려준다
 * 1차 정산은 환불 마감일 다음 날 만들어지므로, 그 전에 취소된 참가권은 빠진다 (정산 전 취소)
 * 대회가 있는지는 주문 서비스가 모르므로, 판매가 없는 대회와 없는 대회 모두 빈 목록이다
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SoldSummaryQueryUseCase {
	private final OrderItemRepository orderItemRepository;

	public List<SoldSummaryDto> summarizeSold(int raceId) {
		return orderItemRepository.summarizeSold(raceId);
	}
}
