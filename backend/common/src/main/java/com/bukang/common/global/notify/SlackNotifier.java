package com.bukang.common.global.notify;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Slack Incoming Webhook으로 글 한 줄을 보낸다.
 * 웹훅 주소는 모든 서비스가 같은 .env의 SLACK_WEBHOOK_URL에서 읽는다. 서비스 yaml에는 적지 않는다
 * (한 서비스만 다른 채널로 보내야 하면 그 서비스 yaml에 slack.webhook-url을 적는다. 그 값이 먼저다).
 *
 * -- KafkaDlt Slack 알림 흐름 --
 * DLT로 받은 메시지
 *  → KafkaDltMessage.from                 값 꺼내기
 *  → 서비스 엔티티 생성자 + save / BaseDltKafkaEvent.markFailedAgain   DLT테이블에 저장·갱신
 *  → KafkaDltAlert.text(행)               알림 글 만들기
 *  → SlackNotifier.sendAfterCommit(글)    커밋 뒤 Slack으로 보내기
 *
 *  다시 보내기 API
 *  → KafkaDltMessage.firstRetryTopic / resendRecord   보낼 곳·메시지 만들기
 *  → kafkaTemplate.send                               보내기
 *  → BaseDltKafkaEvent.markResent                     상태 바꾸기
 */
@Slf4j
@Component
public class SlackNotifier {
	// Slack이 늦어도 호출한 쓰레드(Kafka 리스너 등)를 오래 붙잡지 않도록 짧게 둔다
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

	private final String webhookUrl;
	private final RestClient restClient;

	@Autowired
	public SlackNotifier(@Value("${slack.webhook-url:${SLACK_WEBHOOK_URL:}}") String webhookUrl) {
		this(webhookUrl, RestClient.builder().requestFactory(requestFactory()).build());
	}

	// 테스트에서 MockRestServiceServer에 묶은 RestClient를 넣는다
	SlackNotifier(String webhookUrl, RestClient restClient) {
		this.webhookUrl = webhookUrl;
		this.restClient = restClient;
	}

	// 지금 바로 보낸다
	public void send(String text) {
		if (webhookUrl == null || webhookUrl.isBlank()) {
			log.warn("Slack 웹훅 주소(SLACK_WEBHOOK_URL)가 없어 알림을 보내지 않습니다.\n{}", text);
			return;
		}
		try {
			restClient.post()
				.uri(URI.create(webhookUrl))
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("text", text))
				.retrieve()
				.toBodilessEntity();
		} catch (RuntimeException e) {
			log.error("Slack 알림을 보내지 못했습니다.\n{}", text, e);
		}
	}

	// 지금 트랜잭션이 커밋된 뒤에 보낸다. 롤백되면 보내지 않는다 (기록이 취소됐는데 알림만 가는 일을 막는다)
	// 트랜잭션 밖에서 부르면 바로 보낸다
	public void sendAfterCommit(String text) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			send(text);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				send(text);
			}
		});
	}

	private static SimpleClientHttpRequestFactory requestFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(CONNECT_TIMEOUT);
		factory.setReadTimeout(READ_TIMEOUT);
		return factory;
	}
}
