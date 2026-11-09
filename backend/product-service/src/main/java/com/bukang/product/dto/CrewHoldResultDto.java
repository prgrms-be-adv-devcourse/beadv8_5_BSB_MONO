package com.bukang.product.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 크루 자리 확보 결과 (파티 크루 자리 확보 요청서 형식). 자리가 모자란 것도 오류가 아니라 결과로 돌려준다
// 전부 아니면 실패: 종목 하나라도 모자라면 한 석도 잡지 않고, 모자란 종목을 모두 알려 준다
@AllArgsConstructor
@Getter
public class CrewHoldResultDto {
	// 모든 자리를 잡았는지. false면 모자란 종목이 있어 한 석도 잡지 않았다
	private final boolean held;
	// 자리 끝 시각 (요청한 값 그대로). 못 잡았으면 null
	private final LocalDateTime expiresAt;
	// 못 잡았을 때 모자란 종목 (종목 ID 순서). 잡았으면 빈 배열이다
	// 파티는 종목이 하나라 많아야 한 줄이다
	private final List<CrewHoldShortageDto> shortages;

	public static CrewHoldResultDto held(LocalDateTime expiresAt) {
		return new CrewHoldResultDto(true, expiresAt, List.of());
	}

	public static CrewHoldResultDto shortOf(List<CrewHoldShortageDto> shortages) {
		return new CrewHoldResultDto(false, null, List.copyOf(shortages));
	}
}
