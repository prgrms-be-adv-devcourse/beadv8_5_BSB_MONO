package com.bukang.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.redis.RedisChatMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 대화에 쓰는 ChatClient. 어느 회사 모델인지는 설정(spring.ai.model.chat)이 정하고, 코드는 ChatClient만 쓴다
 * 대화 기록은 Redis에 있고, 최근 max-messages개만 남겨 모델에 보낸다
 */
@Configuration
public class AiChatConfig {
	// 저장소를 RedisChatMemoryRepository로 콕 집어 받는다. Redis 저장소가 빠지면 Spring AI가 조용히 메모리 저장소를 대신 넣는데,
	// 그때는 재시작하면 대화가 사라지므로 앱이 뜨지 않게 한다
	// ChatMemory를 빈으로 등록하지 않는다. ChatMemory 빈이 있으면 Spring AI가 Redis 저장소를 만들지 않는다
	@Bean
	public ChatClient chatClient(
		ChatClient.Builder builder,
		RedisChatMemoryRepository chatMemoryRepository,
		@Value("${ai.chat.max-messages}") int maxMessages
	) {
		MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(chatMemoryRepository)
			.maxMessages(maxMessages)
			.build();
		return builder
			.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
			.build();
	}
}
