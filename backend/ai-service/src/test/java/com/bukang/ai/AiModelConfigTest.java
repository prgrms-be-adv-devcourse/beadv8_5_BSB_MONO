package com.bukang.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import com.bukang.ai.support.AiTestBase;

// 설정만으로 모델 회사가 바뀌는지, OpenAI 키가 없어도 앱이 뜨는지 확인한다 (모델을 부르지는 않는다)
class AiModelConfigTest {
	@Nested
	@DisplayName("test 프로파일은 Ollama 모델을 쓴다 (OpenAI 모델 빈은 없다)")
	class TestProfile extends AiTestBase {
		@Autowired
		private ApplicationContext context;

		@Test
		void usesOllama() {
			assertThat(context.getBeansOfType(OllamaChatModel.class)).hasSize(1);
			assertThat(context.getBeansOfType(OpenAiChatModel.class)).isEmpty();
		}
	}

	// 서버(prod)와 같은 모델 설정: 프로파일 yaml이 덮지 않은 기본값(openai)에 키가 빈 상태
	@Nested
	@DisplayName("기본 설정은 OpenAI 모델을 쓰고, 키가 없어도 앱이 뜬다")
	@SpringBootTest
	// 개발자 PC의 backend/.env나 환경 변수에 키가 있어도 키가 없는 상태로 확인하도록 빈 값으로 고정한다
	@TestPropertySource(properties = {
		"spring.profiles.active=nokey", "spring.ai.openai.api-key=",
		// 회원 서비스 연결도 서버와 같은 http로 띄운다 (부르지는 않는다)
		"ai.clients.member.mode=http", "ai.clients.member.base-url=http://bukang-member-service:8080"
	})
	class DefaultWithoutKey {
		@Autowired
		private ApplicationContext context;

		@DynamicPropertySource
		static void redisProperties(DynamicPropertyRegistry registry) {
			registry.add("spring.ai.chat.memory.repository.redis.host", AiTestBase.REDIS::getRedisHost);
			registry.add("spring.ai.chat.memory.repository.redis.port", AiTestBase.REDIS::getRedisPort);
		}

		@Test
		void startsWithoutKey() {
			assertThat(context.getBean(ChatModel.class)).isInstanceOf(OpenAiChatModel.class);
			assertThat(context.getBeansOfType(OllamaChatModel.class)).isEmpty();
		}
	}
}
