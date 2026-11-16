package com.bukang.order.app;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.common.global.eventpublisher.EventPublisher;
import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.order.domain.RaceScheduleNotice;
import com.bukang.order.out.OrderItemRepository;
import com.bukang.order.out.RaceScheduleNoticeRepository;
import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.out.race.RaceCoursePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 판매 시작 뒤 대회 일시·장소가 바뀌면 결제한 참가권마다 구매자에게 알린다 (BUK-82, 정책 5장 4-4 "알린 날부터 7일 동안")
 * 상품의 대회 변경 메시지(queuing.race.changed)에 실린 일정 변경 일시가 마지막으로 알린 값보다 새로울 때만 보낸다
 * 알린 일시 기록과 알림 발행이 한 트랜잭션이라, 기록이 롤백되면 알림도 나가지 않고 다시 처리할 때 한 번만 나간다
 * shortcut: 기록은 커밋됐는데 Kafka 전송이 실패하면 다시 알리지 않는다 (알림은 잃어도 되는 메시지, 11/04 결정)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RaceScheduleNoticeUseCase {
	private static final DateTimeFormatter RACE_DATE_FORMAT = DateTimeFormatter.ofPattern("M월 d일 HH:mm");
	private static final DateTimeFormatter REFUND_DAY_FORMAT = DateTimeFormatter.ofPattern("M월 d일");

	private final RaceScheduleNoticeRepository raceScheduleNoticeRepository;
	private final OrderItemRepository orderItemRepository;
	private final RaceCoursePort raceCoursePort;
	private final EventPublisher eventPublisher;

	// @return 이번에 보낸 알림 수
	@Transactional
	public int notifyChanged(long raceId, LocalDateTime raceDate, LocalDateTime scheduleChangedAt, LocalDate today) {
		if (scheduleChangedAt == null) {
			return 0;
		}
		RaceScheduleNotice notice = raceScheduleNoticeRepository.findByRaceIdForUpdate(raceId).orElse(null);
		if (notice != null && notice.alreadyNotified(scheduleChangedAt)) {
			return 0;
		}
		// RaceCourseInfo.scheduleChangeRefundLastDay와 같은 셈: 바뀐 날을 1일째로 7일째 날까지
		LocalDate refundLastDay = scheduleChangedAt.toLocalDate()
			.plusDays(RaceCourseInfo.SCHEDULE_CHANGE_REFUND_DAYS - 1);
		if (notice == null) {
			raceScheduleNoticeRepository.saveAndFlush(RaceScheduleNotice.of(raceId, scheduleChangedAt));
		} else {
			notice.markNotified(scheduleChangedAt);
		}
		// 처음 배포할 때 이미 지난 변경까지 알리지 않는다. 기록은 남겨 다음 메시지에서도 건너뛴다
		if (today.isAfter(refundLastDay)) {
			return 0;
		}
		List<OrderItemRepository.ScheduleNoticeTarget> targets = orderItemRepository.findScheduleNoticeTargets(raceId);
		if (targets.isEmpty()) {
			return 0;
		}
		String raceName = raceName(targets.getFirst());
		String body = "%s %s.\n%s까지 전액 환불할 수 있어요.".formatted(raceName, raceDate.format(RACE_DATE_FORMAT),
			refundLastDay.format(REFUND_DAY_FORMAT));
		for (OrderItemRepository.ScheduleNoticeTarget target : targets) {
			eventPublisher.publish(NotificationRequestedEvent.of(target.buyerId(),
				NotificationKind.RACE_SCHEDULE_CHANGED, "대회 일정·장소가 바뀌었어요", body,
				"/tickets/%d-%d/notice".formatted(target.orderId(), target.orderItemId())));
		}
		log.info("대회 일정 변경을 알렸습니다. raceId={}, scheduleChangedAt={}, {}건", raceId, scheduleChangedAt,
			targets.size());
		return targets.size();
	}

	// 상품이 대회명을 주지 않으면 주문 당시 종목 이름으로 대신한다
	private String raceName(OrderItemRepository.ScheduleNoticeTarget target) {
		return raceCoursePort.findCourse(target.raceCourseId())
			.map(RaceCourseInfo::raceName)
			.orElse(target.itemName());
	}
}
