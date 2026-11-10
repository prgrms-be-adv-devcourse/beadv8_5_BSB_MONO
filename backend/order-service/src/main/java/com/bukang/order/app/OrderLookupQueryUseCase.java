package com.bukang.order.app;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.domain.KitDeliveryStatus;
import com.bukang.order.domain.Order;
import com.bukang.order.domain.OrderItem;
import com.bukang.order.domain.ParticipationKitOption;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.domain.exception.OrderNotFoundException;
import com.bukang.order.dto.OrderLookupDto;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationKitOptionRepository;
import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.out.race.RaceCoursePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 문의 서비스가 부르는 주문 요약 조회 (BUK-160). 배송 상태가 바뀌므로 문의 서비스는 저장하지 않고 열 때마다 부른다
 * 주문은 대회·종목 이름을 따로 남기지 않아(상품명 "대회명 종목명"만 있음) 상품 서비스에 묻는다
 * 상품 서비스 호출이 DB 연결을 붙잡지 않도록 주문은 트랜잭션 안에서 다 읽고, 이름은 그 뒤에 묻는다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderLookupQueryUseCase {
	private static final String KIT_WITHOUT_OPTION = "키트";

	private final OrderRepository orderRepository;
	private final ParticipationKitOptionRepository participationKitOptionRepository;
	private final RaceCoursePort raceCoursePort;
	private final TransactionTemplate transactionTemplate;

	public OrderLookupDto findOrder(long orderId) {
		OrderRow order = transactionTemplate.execute(status -> readOrder(orderId));
		Map<Long, Optional<RaceCourseInfo>> courses = new HashMap<>();
		List<OrderLookupDto.Item> items = order.items().stream()
			.map(item -> item.toDto(courses.computeIfAbsent(item.raceCourseId(), this::findCourse)))
			.toList();
		return new OrderLookupDto(order.memberId(), order.orderNo(), order.paymentAmount(), order.status(), items);
	}

	private OrderRow readOrder(long orderId) {
		Order order = orderRepository.findById(orderId)
			.orElseThrow(() -> new OrderNotFoundException("존재하지 않는 주문입니다."));
		List<Long> participationIds = order.getItems().stream().map(OrderItem::getParticipationId).toList();
		Map<Long, String> kitOptions = participationKitOptionRepository
			.findByParticipationIdInOrderByIdAsc(participationIds).stream()
			.collect(Collectors.groupingBy(ParticipationKitOption::getParticipationId,
				Collectors.mapping(option -> option.getOptionName() + " " + option.getOptionValue(),
					Collectors.joining(", "))));
		List<ItemRow> items = order.getItems().stream().map(item -> {
			KitDeliveryStatus delivery = order.kitDeliveryStatus(item);
			String kitSummary = delivery == null ? null
				: kitOptions.getOrDefault(item.getParticipationId(), KIT_WITHOUT_OPTION);
			return new ItemRow(item.getRaceId(), item.getRaceCourseId(), item.getItemName(), kitSummary,
				delivery == null ? null : delivery.name());
		}).toList();
		return new OrderRow(order.getBuyerId(), order.getOrderNo(), order.getTotalAmount(), order.getStatus().name(),
			items);
	}

	// 이름은 보여 주기용이라, 상품 서비스가 멈춰도 문의를 만들고 열 수 있게 실패로 만들지 않는다
	private Optional<RaceCourseInfo> findCourse(long raceCourseId) {
		try {
			return raceCoursePort.findCourse(raceCourseId);
		} catch (ExternalServiceUnavailableException exception) {
			log.warn("주문 요약의 대회·종목 이름을 받지 못해 상품명으로 대신합니다. raceCourseId={}", raceCourseId);
			return Optional.empty();
		}
	}

	private record OrderRow(long memberId, String orderNo, long paymentAmount, String status, List<ItemRow> items) {
	}

	private record ItemRow(long raceId, long raceCourseId, String itemName, String kitSummary,
		String deliveryStatus) {
		OrderLookupDto.Item toDto(Optional<RaceCourseInfo> course) {
			String raceName = course.map(RaceCourseInfo::raceName).orElse(null);
			if (raceName == null) {
				return new OrderLookupDto.Item(raceId, itemName, null, kitSummary, deliveryStatus);
			}
			return new OrderLookupDto.Item(raceId, raceName, course.get().course(), kitSummary, deliveryStatus);
		}
	}
}
