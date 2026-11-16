package com.bukang.ai.support;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.chat.memory.repository.redis.RedisChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.redis.testcontainers.RedisContainer;

/**
 * AI 서비스 통합 테스트의 공통 준비. 모델과 다른 서비스(회원, 도구가 부르는 주문·정산·문의)는 대역으로 바꾸고, 대화 기록은 진짜 Redis에 넣는다 (Docker 필요)
 * Redis는 compose와 같은 redis:8.2다. Spring AI의 Redis 대화 기록은 검색·JSON 기능을 써서 가짜 Redis로는 확인할 수 없다
 * 회원: 3·31은 승인된 판매자, 20은 관리자, 5는 판매자이자 관리자, 1은 일반 회원
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AiTestBase.Stubs.class)
public abstract class AiTestBase {
	protected static final long MEMBER = 1L;
	protected static final long SELLER = 3L;
	protected static final long OTHER_SELLER = 31L;
	protected static final long ADMIN = 20L;
	protected static final long SELLER_AND_ADMIN = 5L;

	// 테스트 클래스가 달라도 컨테이너 하나를 함께 쓴다 (JVM이 끝날 때 Testcontainers가 지운다)
	public static final RedisContainer REDIS = new RedisContainer("redis:8.2");

	static {
		REDIS.start();
	}

	@Autowired
	protected MockMvc mockMvc;
	@Autowired
	protected FakeChatModel chatModel;
	@Autowired
	protected StubMemberRolePort memberRolePort;
	@Autowired
	protected StubOpsPort opsPort;
	@Autowired
	protected RedisChatMemoryRepository chatMemoryRepository;

	@DynamicPropertySource
	static void redisProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.ai.chat.memory.repository.redis.host", REDIS::getRedisHost);
		registry.add("spring.ai.chat.memory.repository.redis.port", REDIS::getRedisPort);
	}

	@BeforeEach
	void resetStubs() {
		chatModel.reset();
		memberRolePort.setUnavailable(false);
		opsPort.reset();
		chatMemoryRepository.findConversationIds().forEach(chatMemoryRepository::deleteByConversationId);
	}

	@TestConfiguration
	static class Stubs {
		// test 프로파일의 Ollama 모델 빈보다 먼저 쓰이게 한다
		@Bean
		@Primary
		FakeChatModel fakeChatModel() {
			return new FakeChatModel();
		}

		@Bean
		StubMemberRolePort stubMemberRolePort() {
			return new StubMemberRolePort(
				Set.of(SELLER, OTHER_SELLER, SELLER_AND_ADMIN), Set.of(ADMIN, SELLER_AND_ADMIN));
		}

		// 진짜 어댑터(HttpOpsAdapter) 대신 쓰이게 한다
		@Bean
		@Primary
		StubOpsPort stubOpsPort() {
			return new StubOpsPort();
		}
	}
}
