package com.bukang.member.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import com.bukang.member.app.MemberKafkaRetry;

/**
 * member가 받는 메시지의 리스너 설정 (BUK-275 서버 확인 결과, BUK-277 환불도 이 팩토리를 쓴다).
 * board-service 구독자 설정처럼 실패하면 1초 → 2초 → 4초 …(최대 30초) 간격으로 10분까지 같은 메시지를 다시 시도하고,
 * 그래도 실패하면 <토픽>.member.dlt로 보낸다. 실패 토픽은 서비스마다 처리한다(서버 확인 결과는 심사 필요로 넘김).
 * 다시 해도 같은 실패(깨진 JSON → IllegalArgumentException)는 바로 실패 토픽으로 보낸다
 * 재시도 토픽 없이 그 자리에서 다시 시도하므로 그동안 같은 파티션의 다음 메시지는 기다린다(늦어지는 것은 괜찮다, server-check-spec)
 */
@Configuration
public class MemberKafkaConfig {
	public static final String LISTENER_FACTORY = "memberKafkaListenerContainerFactory";

	@Bean(LISTENER_FACTORY)
	public ConcurrentKafkaListenerContainerFactory<Object, Object> memberKafkaListenerContainerFactory(
		ConcurrentKafkaListenerContainerFactoryConfigurer configurer, ConsumerFactory<Object, Object> consumerFactory,
		KafkaTemplate<String, String> kafkaTemplate) {
		ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
			new ConcurrentKafkaListenerContainerFactory<>();
		// 접속 · 자동 시작 같은 spring.kafka.listener.* 설정은 부트의 설정기가 넣는다 (test 프로파일은 자동 시작을 끈다)
		configurer.configure(factory, consumerFactory);
		// 파티션은 -1(프로듀서가 고름): 실패 토픽은 자동 생성돼 파티션이 하나라, 원래 파티션 번호로 보내면 실패할 수 있다
		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
			(record, exception) -> new TopicPartition(record.topic() + MemberKafkaRetry.DLT_TOPIC_SUFFIX, -1));
		ExponentialBackOff backOff = new ExponentialBackOff(1_000, 2.0);
		backOff.setMaxInterval(30_000);
		backOff.setMaxElapsedTime(600_000);
		DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
		errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
		factory.setCommonErrorHandler(errorHandler);
		return factory;
	}
}
