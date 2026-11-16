package com.bukang.common.shared.kafka.dlt;

/**
 * DLT 실패 행(BaseDltKafkaEvent)을 Slack 알림 글로 만든다. 보내기는 SlackNotifier.sendAfterCommit으로 한다.
 * 처리 실패가 되면 text(실패 알림), 처리 완료로 닫히면 completedText(완료 알림)를 쓴다.
 * 메시지 본문(payload)은 넣지 않는다. 회원 정보 같은 값이 채널에 남지 않도록, 찾아갈 단서(행 ID, 토픽, message-id, DLT 위치)만 넣는다.
 * 알림 전용 채널(#crewrun-alert)이라 실패 알림 첫 줄에 @channel을 붙여, 각자의 채널 알림 설정과 관계없이 멤버 모두에게 울리게 한다.
 */
public final class KafkaDltAlert {
	// 알림에 넣는 예외 메시지 길이. 테이블에는 전체(최대 1000자)가 있다
	private static final int EXCEPTION_MESSAGE_LENGTH = 300;

	private KafkaDltAlert() {
	}

	// 서비스 이름은 행(event.serviceName())에서 읽는다 (예: product). 내부 API 주소(/internal/v1/{서비스}/kafka-failures)에도 쓴다
	// 다시 보낸 적이 있으면(resentDate가 있으면) "다시 보낸 뒤 또 실패"로 적는다
	public static String text(BaseDltKafkaEvent event) {
		String service = event.serviceName();
		String when = event.getResentDate() == null ? "처음 실패" : "다시 보낸 뒤 또 실패";
		String api = "/internal/v1/" + service + "/kafka-failures/" + event.getId();
		return """
			<!channel> :rotating_light: [%s] Kafka 메시지 처리 실패 (%s)
			• 행 ID: %d
			• 원래 토픽: %s
			• message-id: %s
			• 예외: %s
			• DLT 위치: %s / 파티션 %d / 오프셋 %d
			• 처리: 원인을 고친 뒤 POST %s/resend (다시 보내기) 또는 %s/complete (메모와 함께 닫기)"""
			.formatted(
				service, when,
				event.getId(),
				escape(event.getOriginalTopic()),
				event.getMessageId() == null ? "없음" : escape(event.getMessageId()),
				escape(exceptionSummary(event)),
				escape(event.getDltTopic()), event.getDltPartition(), event.getDltOffset(),
				api, api
			);
	}

	// 처리 실패였던 행이 처리 완료로 닫혔음을 알린다. 실패 알림을 받은 사람이 끝을 알 수 있게 한다
	// 어떻게 끝났는지는 메모로 안다. 다시 보낸 메시지가 처리됐으면(completeByResend) "다시 보내 처리됨",
	// 사람이 닫았으면(completeManually) 그 사람이 적은 메모다
	// 할 일이 남지 않은 알림이라 <!channel>을 붙이지 않는다
	public static String completedText(BaseDltKafkaEvent event) {
		return """
			:white_check_mark: [%s] Kafka 메시지 처리 완료
			• 행 ID: %d
			• 원래 토픽: %s
			• message-id: %s
			• 메모: %s"""
			.formatted(
				event.serviceName(),
				event.getId(),
				escape(event.getOriginalTopic()),
				event.getMessageId() == null ? "없음" : escape(event.getMessageId()),
				escape(event.getCompleteMemo())
			);
	}

	// "DataIntegrityViolationException - 메시지 앞 300자" 형태. 예외 종류는 패키지를 떼고 클래스 이름만 쓴다
	private static String exceptionSummary(BaseDltKafkaEvent event) {
		String exceptionClass = event.getExceptionClass();
		String simpleName = exceptionClass == null ? "알 수 없음"
			: exceptionClass.substring(exceptionClass.lastIndexOf('.') + 1);
		String message = event.getExceptionMessage();
		if (message == null) {
			return simpleName;
		}
		if (message.length() > EXCEPTION_MESSAGE_LENGTH) {
			message = message.substring(0, EXCEPTION_MESSAGE_LENGTH) + "…";
		}
		return simpleName + " - " + message;
	}

	// Slack은 &, <, >를 서식 기호로 읽는다(<!channel>이면 채널 전체 알림). 예외 메시지 등 밖에서 온 값은 글자로 바꿔,
	// 값 안에 우연히 섞인 기호가 알림을 바꾸지 못하게 한다. 첫 줄의 <!channel>은 우리가 넣은 것이라 바꾸지 않는다
	private static String escape(String value) {
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
