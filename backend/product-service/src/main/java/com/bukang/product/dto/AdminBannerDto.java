package com.bukang.product.dto;

import java.time.LocalDateTime;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.product.domain.BannerHiddenReason;
import com.bukang.product.domain.Race;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 관리자 홈 배너 화면의 한 줄 (CRU-147). 홈에 보이지 않는 대회도 이유와 함께 남는다
@AllArgsConstructor
@Getter
public class AdminBannerDto {
	private final long raceId;
	private final String title;
	private final RaceSaleStatus saleStatus;
	private final LocalDateTime applicationCloseAt;
	// 홈에 보이는 순서 (0부터)
	private final int displayOrder;
	// 지금 홈에 보이는지
	private final boolean visible;
	// 보이지 않는 이유와 화면 이름. 보이면 둘 다 null
	private final BannerHiddenReason hiddenReason;
	private final String hiddenReasonLabel;

	public static AdminBannerDto of(Race race, int displayOrder, LocalDateTime now) {
		BannerHiddenReason reason = race.bannerHiddenReason(now);
		return new AdminBannerDto(race.getId(), race.getTitle(), race.getSaleStatus(), race.getApplicationCloseAt(),
			displayOrder, reason == null, reason, reason == null ? null : reason.getLabel());
	}
}
