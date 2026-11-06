package com.bukang.notification.app;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.notification.domain.Notification;
import com.bukang.notification.domain.exception.NotificationNotFoundException;
import com.bukang.notification.dto.NoticeDto;
import com.bukang.notification.out.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationUseCase {
	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

	private final NotificationRepository notificationRepository;

	// 받은 알림을 저장한다. 같은 메시지 ID가 이미 있으면 건너뛴다 (두 번 받아도 알림 하나)
	// 동시에 같은 메시지가 오면 message_id 유니크 제약이 마지막으로 막는다. 그 밖의 저장 실패(글자 수 초과 등)는 경고로 남기고 버린다
	public void save(String messageId, NotificationRequestedEvent event) {
		if (notificationRepository.existsByMessageId(messageId)) {
			log.info("이미 받은 알림이라 건너뜁니다. messageId={}, memberId={}", messageId, event.memberId());
			return;
		}
		try {
			notificationRepository.saveAndFlush(Notification.from(messageId, event));
		} catch (DataIntegrityViolationException failure) {
			log.warn("알림을 저장하지 못해 버립니다 (동시에 온 같은 메시지이거나 값이 표에 맞지 않음). messageId={}, memberId={}, kind={}",
				messageId, event.memberId(), event.kind(), failure);
		}
	}

	@Transactional(readOnly = true)
	public List<NoticeDto> findMine(long memberId) {
		LocalDateTime now = now();
		return notificationRepository.findByMemberIdOrderByOccurredAtDescIdDesc(memberId).stream()
			.map(notification -> NotificationView.toNotice(notification, now))
			.toList();
	}

	// 남의 알림이면 없는 알림과 같이 404로 답한다
	@Transactional
	public void read(long memberId, long notificationId) {
		notificationRepository.findByIdAndMemberId(notificationId, memberId)
			.orElseThrow(NotificationNotFoundException::new)
			.markRead(now());
	}

	@Transactional
	public int readAll(long memberId) {
		return notificationRepository.markAllRead(memberId, now());
	}

	// 일이 생긴 지 보관 기간이 지난 알림을 지운다 (11/04 결정 7일)
	@Transactional
	public int deleteExpired(int retentionDays) {
		return notificationRepository.deleteOccurredBefore(now().minusDays(retentionDays));
	}

	private static LocalDateTime now() {
		return LocalDateTime.now(KOREA);
	}
}
