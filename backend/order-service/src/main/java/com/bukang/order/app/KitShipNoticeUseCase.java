package com.bukang.order.app;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.global.eventpublisher.EventPublisher;
import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.order.domain.KitShipNotice;
import com.bukang.order.out.KitShipNoticeRepository;
import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.out.race.RaceCoursePort;
import com.bukang.order.out.seller.SellerLookupPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 키트를 보낼 수 있게 된 대회의 판매자에게 「키트를 보낼 참가자가 있어요」를 한 번 보낸다 (11/06 판매자 알림 결정)
 * 보낼 수 있는 때는 발송(KitDeliveryUseCase.ship)과 같은 기준이다: 환불할 수 있는 동안(환불 기한 날까지, 일정 변경 뒤 7일 안,
 * 취소된 대회)은 보낼 수 없으므로 알리지 않는다
 * 대회마다 한 번이다(KitShipNotice, 대회 ID 유니크). 알림 기록과 알림 발행이 한 트랜잭션이라, 기록이 롤백되면 알림도 나가지 않는다
 * 알림은 잃어도 되는 메시지라(11/04 결정) 상품·판매자 조회가 실패하면 그 대회만 건너뛰고 다음 배치에서 다시 본다
 * shortcut: 기록은 커밋됐는데 Kafka 전송이 실패하면 그 대회는 다시 알리지 않는다. 알림 뒤에 결제된 키트도 따로 알리지 않는다
 * (환불 기한은 대회 단위라 종목마다 다르지 않다). 판매자가 놓치는 일이 생기면 아웃박스나 종목·건수 단위 재알림으로 바꾼다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KitShipNoticeUseCase {
	private final KitShipNoticeRepository kitShipNoticeRepository;
	private final RaceCoursePort raceCoursePort;
	private final SellerLookupPort sellerLookupPort;
	private final EventPublisher eventPublisher;
	private final TransactionTemplate transactionTemplate;

	// @return 이번에 알린 대회 수
	public int notifyShippable(LocalDateTime now) {
		Set<Long> noticedRaceIds = new HashSet<>();
		for (long courseId : kitShipNoticeRepository.findReadyKitCourseIdsNotNoticed()) {
			try {
				RaceCourseInfo course = raceCoursePort.findCourse(courseId).orElse(null);
				if (course == null || noticedRaceIds.contains(course.raceId())
					|| course.isRefundableOn(now.toLocalDate())) {
					continue;
				}
				if (notifySeller(course, now)) {
					noticedRaceIds.add(course.raceId());
				}
			} catch (DataIntegrityViolationException exception) {
				// 다른 서버가 같은 대회를 먼저 알렸다
				log.info("이미 키트 발송을 알린 대회입니다. raceCourseId={}", courseId);
			} catch (RuntimeException exception) {
				log.error("키트 발송 알림을 보내지 못했습니다. raceCourseId={}", courseId, exception);
			}
		}
		return noticedRaceIds.size();
	}

	private boolean notifySeller(RaceCourseInfo course, LocalDateTime now) {
		Long memberId = sellerLookupPort.findSellerMemberId(course.sellerId()).orElse(null);
		if (memberId == null) {
			log.warn("판매자를 찾지 못해 키트 발송을 알리지 못했습니다. raceId={}, sellerId={}", course.raceId(),
				course.sellerId());
			return false;
		}
		return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
			if (kitShipNoticeRepository.existsByRaceId(course.raceId())) {
				return false;
			}
			kitShipNoticeRepository.saveAndFlush(KitShipNotice.of(course.raceId(), course.sellerId(), now));
			eventPublisher.publish(NotificationRequestedEvent.of(memberId, NotificationKind.KIT_SHIPMENT_REQUIRED,
				"키트를 보낼 참가자가 있어요",
				"%s 환불 기한이 지나 키트를 보낼 수 있어요.\n키트 배송 관리에서 송장 번호를 넣어 주세요."
					.formatted(course.courseName()),
				"/seller/kits"));
			return true;
		}));
	}
}
