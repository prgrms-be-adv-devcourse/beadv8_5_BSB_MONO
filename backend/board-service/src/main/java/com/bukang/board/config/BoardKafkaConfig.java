package com.bukang.board.config;

import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * 색인 메시지를 받는 리스너용 컨테이너 설정 (BUK-179). Elasticsearch가 잠깐 멈춰도 메시지를 잃지 않게,
 * 실패하면 1초 → 2초 → 4초 …(최대 30초) 간격으로 10분까지 같은 메시지를 다시 시도한다.
 * 기본 오류 처리(바로 9번 다시 시도하고 버림)는 몇 초 만에 끝나 장애 중 메시지를 잃기 때문이다.
 * 회원 이벤트 리스너는 이 설정을 쓰지 않고 기본 팩토리를 그대로 쓴다
 * shortcut: 10분을 넘겨 멈추면 그 메시지는 버려진다. 시작할 때 도는 색인 채우기(BoardPostReindexUseCase)가 바로잡는다
 */
@Configuration
public class BoardKafkaConfig {
	public static final String SEARCH_LISTENER_FACTORY = "boardSearchKafkaListenerContainerFactory";

	@Bean(SEARCH_LISTENER_FACTORY)
	public ConcurrentKafkaListenerContainerFactory<Object, Object> boardSearchKafkaListenerContainerFactory(
		ConcurrentKafkaListenerContainerFactoryConfigurer configurer, ConsumerFactory<Object, Object> consumerFactory) {
		ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
			new ConcurrentKafkaListenerContainerFactory<>();
		// 접속·자동 시작 같은 spring.kafka.listener.* 설정은 부트의 설정기가 넣는다 (test 프로파일은 자동 시작을 끈다)
		configurer.configure(factory, consumerFactory);
		ExponentialBackOff backOff = new ExponentialBackOff(1_000, 2.0);
		backOff.setMaxInterval(30_000);
		backOff.setMaxElapsedTime(600_000);
		factory.setCommonErrorHandler(new DefaultErrorHandler(backOff));
		return factory;
	}
}
