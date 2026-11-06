package com.bukang.notification.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.bukang.notification.domain.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
	// 내 알림 최신순 (7일 정리 뒤라 많지 않아 페이지를 나누지 않는다)
	List<Notification> findByMemberIdOrderByOccurredAtDescIdDesc(long memberId);

	boolean existsByMessageId(String messageId);

	Optional<Notification> findByIdAndMemberId(long id, long memberId);

	@Modifying
	@Query("update Notification n set n.readAt = :now where n.memberId = :memberId and n.readAt is null")
	int markAllRead(long memberId, LocalDateTime now);

	@Modifying
	@Query("delete from Notification n where n.occurredAt < :before")
	int deleteOccurredBefore(LocalDateTime before);
}
