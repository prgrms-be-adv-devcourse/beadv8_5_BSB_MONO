package com.bukang.common.shared.recordcert.event;

import java.util.Map;
import java.util.UUID;

/**
 * 기록증 서버 확인 요청 (BUK-274 발행, BUK-275 처리). member가 제출을 저장한 트랜잭션에서 아웃박스로 남기고, 커밋 뒤 보낸다.
 * ai-service가 받아 원본 사진의 칸별 상자 자리를 다시 읽는다. member에 되묻지 않게 읽는 데 필요한 값을 모두 싣는다
 *
 * @param eventId 받는 쪽이 같은 이벤트를 두 번 처리하지 않게 거르는 값 (UUID, Kafka 헤더 eventId와 같다)
 * @param fieldBoxes 칸(NET_TIME · NET_LABEL · RACE_DATE · COURSE)별 원본 픽셀 네 점 [[x, y], ...]. 참가자가 직접 입력한 칸은 빠진다
 * @param specVersion 브라우저 OCR 명세 버전. 서버 값과 다르면 읽지 않고 모든 칸을 읽지 못함으로 보낸다
 * @param recModelSha256 브라우저가 쓴 인식 모델의 sha256. 직접 입력만 했으면 null
 */
public record RecordCertCheckRequestedEvent(
	String eventId,
	long submissionId,
	long fileId,
	Map<String, double[][]> fieldBoxes,
	String specVersion,
	String recModelSha256
) {
	public static RecordCertCheckRequestedEvent of(long submissionId, long fileId, Map<String, double[][]> fieldBoxes,
		String specVersion, String recModelSha256) {
		return new RecordCertCheckRequestedEvent(UUID.randomUUID().toString(), submissionId, fileId, fieldBoxes,
			specVersion, recModelSha256);
	}
}
