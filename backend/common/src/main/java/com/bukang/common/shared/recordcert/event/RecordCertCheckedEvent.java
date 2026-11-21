package com.bukang.common.shared.recordcert.event;

import java.util.Map;
import java.util.UUID;

/**
 * 기록증 서버 확인 결과 (BUK-275). ai-service가 "확인 요청"을 읽고 보내고, member가 받아 판정한다
 * 해석 · 비교 · 판정은 member가 하므로 읽은 글자만 싣는다
 *
 * @param eventId 이벤트 ID (UUID, Kafka 헤더 eventId와 같다)
 * @param texts 칸(NET_TIME · NET_LABEL · RACE_DATE · COURSE)별 읽은 글자. 없는 칸은 읽지 못함(상자 없음 · 글자 없음)
 * @param failReason 끝내 읽지 못한 이유. 정상이면 null이고, 실패면 texts가 비어 모든 칸이 읽지 못함이 된다
 */
public record RecordCertCheckedEvent(
	String eventId,
	long submissionId,
	Map<String, String> texts,
	String failReason
) {
	public static RecordCertCheckedEvent read(long submissionId, Map<String, String> texts) {
		return new RecordCertCheckedEvent(UUID.randomUUID().toString(), submissionId, texts, null);
	}

	public static RecordCertCheckedEvent failed(long submissionId, String failReason) {
		return new RecordCertCheckedEvent(UUID.randomUUID().toString(), submissionId, Map.of(), failReason);
	}
}
