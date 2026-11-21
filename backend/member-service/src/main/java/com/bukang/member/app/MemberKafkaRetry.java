package com.bukang.member.app;

/**
 * member가 받는 Kafka 메시지의 재시도 · 실패 토픽 규칙 (BUK-275 서버 확인 결과, BUK-277 환불도 같은 값을 쓴다)
 * 받는 쪽(MemberKafkaConfig)과 실패 토픽 리스너가 같은 값을 쓰도록 한곳에 둔다
 */
public final class MemberKafkaRetry {
	// 실패 토픽 접미사 (예: queuing.recordcert.checked.member.dlt). 환불 토픽처럼 다른 서비스도 받는 토픽과 겹치지 않게 서비스 이름을 넣는다
	public static final String DLT_TOPIC_SUFFIX = ".member.dlt";
	// 실패 토픽 리스너의 컨슈머 그룹. 본 리스너와 offset을 따로 관리한다
	public static final String DLT_GROUP_ID = "member-dlt";

	private MemberKafkaRetry() {
	}
}
