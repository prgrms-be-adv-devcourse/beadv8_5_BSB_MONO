package com.bukang.ai.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

// 대시보드 「크루런 AI 브리핑」 카드. 모양은 프론트 AxBriefing 타입(entities/ai-assistant)과 같다
public record AiBriefingDto(String headline, String basis, List<Todo> todos, List<Metric> metrics) {
	// 먼저 할 일 한 줄. href는 눌러서 가는 프론트 경로
	public record Todo(String title, String description, String linkLabel, String href) {
	}

	// 지표 한 칸. noteTone(good·bad)은 없으면 응답에서 뺀다
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Metric(String label, String value, String note, String noteTone) {
	}
}
