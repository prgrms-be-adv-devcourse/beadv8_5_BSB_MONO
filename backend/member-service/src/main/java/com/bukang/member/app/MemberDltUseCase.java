package com.bukang.member.app;

import org.springframework.stereotype.Service;

import com.bukang.common.global.notify.SlackNotifier;
import com.bukang.common.shared.kafka.dlt.KafkaDltAlert;
import com.bukang.common.shared.kafka.dlt.KafkaDltMessage;
import com.bukang.member.domain.MemberDltKafkaEvent;
import com.bukang.member.out.MemberDltKafkaEventRepository;

import lombok.RequiredArgsConstructor;

/**
 * 실패 토픽으로 간 메시지를 실패 테이블(MEMBER_DLT_KAFKA_EVENT)에 남기고 커밋 뒤 Slack으로 알린다 (BUK-275)
 * 트랜잭션은 부른 쪽이 연다. 다시 보내기 API는 두지 않는다: 서버 확인 결과는 실패 토픽에서 심사 필요로 넘겨 끝나므로
 * 다시 보내도 확인 중이 아니어서 무시된다
 * shortcut: 다시 보내기 · 처리 완료 API가 없다. 다시 처리해야 하는 메시지(BUK-277 환불 등)가 생기면 file-service의 FileDltUseCase처럼 붙인다
 */
@Service
@RequiredArgsConstructor
public class MemberDltUseCase {
	private final MemberDltKafkaEventRepository memberDltKafkaEventRepository;
	private final SlackNotifier slackNotifier;

	// 같은 DLT 메시지를 두 번 받으면(message-id가 같으면) 다시 남기지 않는다. message-id가 없는 메시지는 늘 새 행이다
	public void record(KafkaDltMessage message) {
		if (message.messageId() != null
			&& memberDltKafkaEventRepository.findByMessageId(message.messageId()).isPresent()) {
			return;
		}
		MemberDltKafkaEvent saved = memberDltKafkaEventRepository.save(new MemberDltKafkaEvent(message));
		slackNotifier.sendAfterCommit(KafkaDltAlert.text(saved));
	}
}
