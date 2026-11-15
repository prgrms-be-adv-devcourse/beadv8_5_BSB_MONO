package com.bukang.product.app;

import com.bukang.product.domain.BaseRaceSyncEvent;
import com.bukang.product.domain.RaceSyncType;

/**
 * 검색 문서 동기화 행(문서·임베딩)의 Slack 알림 글을 만든다 (BUK-162). 보내기는 SlackNotifier.sendAfterCommit으로 한다.
 * Kafka DLT 알림(KafkaDltAlert)과 같은 모양이다. 실패하면 failedText(@channel), 실패했던 행이 처리되면 completedText.
 * 대회 소개 같은 본문과 예외 메시지는 넣지 않는다. 표에 예외를 남기지 않으므로 원인은 서버 로그에서 raceId로 찾는다.
 */
public final class RaceSyncAlert {
	// 재처리 API 주소 앞부분 (InternalV1RaceSyncFailureController)
	static final String API_BASE = "/internal/v1/product/race-sync-failures/";
	// 스케줄러가 다시 처리해 완료됐을 때 알림에 적는 메모
	public static final String AUTO_COMPLETE_MEMO = "다시 처리됨";

	private RaceSyncAlert() {
	}

	// 처리 실패(FAILED)가 됐을 때. 실패 알림이 간 적 있는 행이 다시 FAILED가 되면 "또 실패"로 적는다
	// failedDate는 markFailed가 이번 실패 시각으로 덮어쓰므로, 이전에 실패한 적이 있는지는 부른 쪽이 넘긴다
	public static String failedText(RaceSyncType type, BaseRaceSyncEvent event, boolean failedBefore) {
		String when = failedBefore ? "또 실패" : "처음 실패";
		String api = API_BASE + type.getPath() + "/" + event.getId() + "/retry";
		return """
			<!channel> :rotating_light: [product] %s 실패 (%s)
			• 행 ID: %d / 대회 ID: %d / 실패 횟수: %d
			• 원인: 서버 로그에서 raceId=%d로 찾기
			• 처리: 원인을 고친 뒤 POST %s (재처리)"""
			.formatted(
				type.getLabel(), when,
				event.getId(), event.getRaceId(), event.getRetryCount(),
				event.getRaceId(),
				api
			);
	}

	// 실패 알림이 간 행이 처리 완료가 됐을 때. 어떻게 끝났는지는 메모로 안다
	//   스케줄러가 다시 처리함(재처리 API로 되돌렸든 DB에서 되돌렸든 같다): AUTO_COMPLETE_MEMO
	//   사람이 문서를 직접 고치고 닫음(문서 동기화만): 그 사람이 적은 메모
	// 할 일이 남지 않은 알림이라 <!channel>을 붙이지 않는다
	public static String completedText(RaceSyncType type, BaseRaceSyncEvent event, String memo) {
		return """
			:white_check_mark: [product] %s 처리 완료
			• 행 ID: %d / 대회 ID: %d
			• 메모: %s"""
			.formatted(type.getLabel(), event.getId(), event.getRaceId(), escape(memo));
	}

	// Slack은 &, <, >를 서식 기호로 읽는다. 사람이 적은 메모 안의 기호가 알림을 바꾸지 못하게 글자로 바꾼다
	private static String escape(String value) {
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
