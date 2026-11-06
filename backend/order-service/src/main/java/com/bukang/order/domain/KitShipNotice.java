package com.bukang.order.domain;

import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매자에게 「키트를 보낼 참가자가 있어요」를 보낸 대회 (KitShipNoticeUseCase)
 * 대회마다 한 줄이라 여러 서버가 같은 배치를 함께 돌려도 알림은 한 번만 나간다 (대회 ID 유니크)
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "ORDER_KIT_SHIP_NOTICE",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_kit_ship_notice_race", columnNames = {"race_id"})
	}
)
public class KitShipNotice extends BaseIdAndTime {
	@Column(name = "race_id", nullable = false)
	private long raceId;

	@Column(name = "seller_id", nullable = false)
	private long sellerId;

	@Column(name = "notified_at", nullable = false)
	private LocalDateTime notifiedAt;

	public static KitShipNotice of(long raceId, long sellerId, LocalDateTime notifiedAt) {
		KitShipNotice notice = new KitShipNotice();
		notice.raceId = raceId;
		notice.sellerId = sellerId;
		notice.notifiedAt = notifiedAt;
		return notice;
	}
}
