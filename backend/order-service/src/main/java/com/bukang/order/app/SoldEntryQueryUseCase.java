package com.bukang.order.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.order.domain.OrderItemStatus;
import com.bukang.order.dto.SoldEntriesDto;
import com.bukang.order.dto.SoldEntryDto;
import com.bukang.order.out.OrderItemRepository;

import lombok.RequiredArgsConstructor;

/**
 * 정산 서비스(E)가 최종 정산 생성 배치에서 부르는 판매 내역 조회 (payout SoldEntryPort.findSoldEntries)
 * 대회의 결제 완료 · 취소 중 참가권을 코스로 묶어 돌려준다 (BUK-244). 정산할 대회는 정산 서비스가 상품 서비스의 대회일로 정하고 대회 ID로 묻는다
 * 대회가 있는지는 주문 서비스가 모르므로, 판매가 없는 대회와 없는 대회 모두 빈 목록이다
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SoldEntryQueryUseCase {
	private final OrderItemRepository orderItemRepository;

	// 묶기는 메모리에서 한다. 쿼리는 그대로 두고 ID 목록만 나누면 되고, 대회 하나는 많아야 수만 줄이다
	// shortcut: 판매자 · 대회일 · 코스 가격은 첫 줄 값을 쓴다(정책상 같음). 섞일 수 있게 바뀌면 묶는 기준에 넣는다
	public SoldEntriesDto findSoldEntries(long raceId) {
		List<SoldEntryDto> rows = orderItemRepository.findSoldEntries(raceId);
		if (rows.isEmpty()) {
			return new SoldEntriesDto(raceId, null, null, List.of());
		}
		Map<Long, SoldEntriesDto.Course> courses = new TreeMap<>();
		for (SoldEntryDto row : rows) {
			SoldEntriesDto.Course course = courses.computeIfAbsent(row.getRaceCourseId(),
				id -> new SoldEntriesDto.Course(id, row.getPrice(), new ArrayList<>(), new ArrayList<>()));
			List<Long> ids = row.getStatus() == OrderItemStatus.PAID ? course.paidIds() : course.blockingIds();
			ids.add(row.getOrderItemId());
		}
		SoldEntryDto first = rows.getFirst();
		return new SoldEntriesDto(raceId, first.getRaceDate(), first.getSellerId(), List.copyOf(courses.values()));
	}
}
