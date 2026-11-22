package com.bukang.member.recordcert.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

// 판매자의 심사 필요 건수 (BUK-276). 대시보드 "처리할 일" 카드와 AI 브리핑이 부른다
public record RecordCertReviewCountDto(
	@Schema(description = "내 대회 전체의 심사 필요 제출 수", example = "12")
	long waitingCount,
	@Schema(description = "심사 필요 제출이 있는 대회별 건수. 건수가 많은 순, 같으면 최근 대회 순. 0건인 대회는 없다")
	List<RaceCount> races
) {
	// 대시보드가 대회마다 한 줄씩 그 대회의 심사 화면으로 보내는 데 쓴다. 대회 이름은 화면이 내 대회 목록에서 붙인다
	public record RaceCount(
		@Schema(description = "대회 ID", example = "206")
		long raceId,
		@Schema(description = "그 대회의 심사 필요 제출 수", example = "3")
		long waitingCount
	) {
	}

	public static RecordCertReviewCountDto of(List<RaceCount> races) {
		return new RecordCertReviewCountDto(races.stream().mapToLong(RaceCount::waitingCount).sum(), races);
	}
}
