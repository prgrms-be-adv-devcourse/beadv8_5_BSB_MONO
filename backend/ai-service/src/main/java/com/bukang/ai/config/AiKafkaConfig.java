package com.bukang.ai.config;

import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * 확인 요청 리스너용 컨테이너 설정 (BUK-275). 결과를 보내지 못하면 offset을 넘기지 않고 같은 요청을 다시 처리한다
 * 1초 → 2초 → 4초 …(최대 30초) 간격으로 끝없이 다시 시도한다(board-service 구독자 설정에서 시간 상한만 뺐다).
 * 기본 오류 처리(바로 9번 시도하고 버림)는 Kafka가 잠깐 멈출 때 요청을 버려 제출이 확인 중으로 남기 때문이다.
 * 읽을 수 없는 요청(깨진 JSON → IllegalArgumentException)은 다시 해도 같아 로그만 남기고 넘긴다
 */
@Configuration
public class AiKafkaConfig {
	public static final String LISTENER_FACTORY = "aiRecordCertKafkaListenerContainerFactory";

	@Bean(LISTENER_FACTORY)
	public ConcurrentKafkaListenerContainerFactory<Object, Object> aiRecordCertKafkaListenerContainerFactory(
		ConcurrentKafkaListenerContainerFactoryConfigurer configurer, ConsumerFactory<Object, Object> consumerFactory) {
		ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
			new ConcurrentKafkaListenerContainerFactory<>();
		configurer.configure(factory, consumerFactory);
		ExponentialBackOff backOff = new ExponentialBackOff(1_000, 2.0);
		backOff.setMaxInterval(30_000);
		DefaultErrorHandler errorHandler = new DefaultErrorHandler(backOff);
		errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
		factory.setCommonErrorHandler(errorHandler);
		return factory;
	}
}
