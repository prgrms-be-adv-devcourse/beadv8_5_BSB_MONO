package com.bukang.order.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.order.domain.OrderItemStatus;
import com.bukang.order.dto.SoldEntriesDto;
import com.bukang.order.dto.SoldEntriesFormat;
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
	// 판매자 · 코스 가격은 정책상 하나라 맨 위 · 코스에 한 번만 담는다. 다르면 정산이 틀린 돈을 내지 않게 500으로 막는다
	// shortcut: 대회일은 일정 변경 뒤 섞일 수 있어 첫 줄 값을 쓴다(정산 기준일은 상품에서 받음). 섞임을 알려야 하면 묶는 기준에 넣는다
	// 결제 완료 ID를 format대로 담는다. 묶는 기준 · blockingIds는 format과 상관없이 같다
	public SoldEntriesDto findSoldEntries(long raceId, SoldEntriesFormat format) {
		List<SoldEntryDto> rows = orderItemRepository.findSoldEntries(raceId);
		if (rows.isEmpty()) {
			return new SoldEntriesDto(raceId, null, null, List.of());
		}
		SoldEntryDto first = rows.getFirst();
		Map<Long, SoldEntriesDto.Course> courses = new TreeMap<>();
		for (SoldEntryDto row : rows) {
			SoldEntriesDto.Course course = courses.computeIfAbsent(row.getRaceCourseId(),
				id -> new SoldEntriesDto.Course(id, row.getPrice(), new ArrayList<>(), new ArrayList<>()));
			if (row.getPrice() != course.price() || row.getSellerId() != first.getSellerId()) {
				throw new IllegalStateException("판매 내역의 코스 가격 또는 판매자가 섞여 있습니다. raceId=" + raceId
					+ ", orderItemId=" + row.getOrderItemId());
			}
			List<Long> ids = row.getStatus() == OrderItemStatus.PAID ? course.paidIds() : course.blockingIds();
			ids.add(row.getOrderItemId());
		}
		List<SoldEntriesDto.Course> encoded = courses.values().stream()
			.map(course -> SoldIdsEncoder.encode(course, format))
			.toList();
		return new SoldEntriesDto(raceId, first.getRaceDate(), first.getSellerId(), encoded);
	}
}
