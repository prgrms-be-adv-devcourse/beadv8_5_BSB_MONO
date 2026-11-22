package com.bukang.product.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// 홈 배너 목록 전체 (CRU-147). 보낸 순서가 홈에 보이는 순서다. 빈 목록이면 배너를 모두 비운다
// 칸이 하나뿐이라 record로 받는다 (Lombok 생성자 하나짜리 클래스는 Jackson이 본문 전체를 그 값으로 읽으려 할 수 있다)
@Schema(description = "홈 배너 저장 요청")
public record HomeBannerSaveRequestDto(
	@Schema(description = "배너로 고른 대회 ID (보이는 순서대로, 최대 5개, 중복 없음)", example = "[3, 1, 7]")
	@NotNull(message = "배너로 고른 대회 목록을 보내주세요.")
	List<@NotNull(message = "대회 ID를 작성해주세요.") @Positive(message = "대회 ID는 0보다 커야 합니다.") Long> raceIds
) {
}
