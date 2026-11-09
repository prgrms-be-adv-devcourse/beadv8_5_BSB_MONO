package com.bukang.notification.dto;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 알림 화면 한 줄. 프론트 use-notifications.ts의 Notice 모양 그대로다
 */
@Schema(description = "내 알림 하나")
public record NoticeDto(
	@Schema(description = "알림 ID", example = "12")
	String id,
	@Schema(description = "알림 종류 이름 (화면 분류는 프론트가 정함)", example = "CREW_JOINED")
	String kind,
	@Schema(description = "알림이 만들어진 시각 (서울 +09:00)", example = "2026-11-02T10:00:00+09:00")
	OffsetDateTime sentAt,
	@Schema(description = "아이콘 이름 (알림 종류로 정함)", example = "CircleCheck")
	String icon,
	@Schema(description = "제목", example = "환불이 끝났어요")
	String title,
	@Schema(description = "내용 (줄바꿈 포함)", example = "가을 트레일 21K ·\n₩60,000이 예치금으로 들어왔어요.")
	String body,
	@Schema(description = "보여 줄 시각 (한국 시간 기준: 방금, N분 전, N시간 전, 어제 이전은 M/D)", example = "5시간 전")
	String at,
	@Schema(description = "묶음: today(오늘), earlier(이전)", example = "today")
	String group,
	@Schema(description = "누르면 갈 프론트 경로", example = "/me/deposit")
	String href,
	@Schema(description = "안 읽었으면 true", example = "true")
	boolean unread
) {
}
