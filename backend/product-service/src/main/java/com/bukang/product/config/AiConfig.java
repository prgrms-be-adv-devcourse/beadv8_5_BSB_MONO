package com.bukang.product.config;

import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bukang.product.domain.document.EmbeddingVector;

/**
 * AI 추천 임베딩 설정 (BUK-162)
 * - 어느 회사 모델을 쓸지는 yaml의 spring.ai.model.embedding으로 고른다 (prod=openai, dev·test=ollama)
 * - 벡터 차원의 원본은 검색 문서의 dense_vector 칸과 같은 EmbeddingVector.DIMENSIONS 하나다. yaml에는 적지 않는다
 */
@Configuration
public class AiConfig {
	// 임베딩을 부를 때마다 넘기는 옵션 (차원 = EmbeddingVector.DIMENSIONS)
	// OpenAI 빈은 Spring AI가 yaml만 보고 만들어 상수를 모르므로, 부를 때 차원을 넘긴다 (모델 기본값 1536이 나오지 않게)
	// 임베딩을 부르는 곳은 모두 이 옵션을 넘긴다: embeddingModel.call(new EmbeddingRequest(글 목록, embeddingOptions))
	@Bean
	public EmbeddingOptions embeddingOptions() {
		return EmbeddingOptions.builder()
			.dimensions(EmbeddingVector.DIMENSIONS)
			.build();
	}

	// Spring AI 2.0.1의 Ollama 임베딩 설정(spring.ai.ollama.embedding.*)에는 dimensions가 없어,
	// yaml에 적어도 768차원이 그대로 나온다. 그래서 차원을 정한 빈을 직접 만든다
	// (부를 때 넘기는 옵션을 Ollama가 따르는지와 상관없이 384가 나오게 빈에도 넣어 둔다)
	// 이 빈이 있으면 Spring AI가 자동으로 만드는 같은 이름의 빈은 만들어지지 않는다
	@Bean
	@ConditionalOnProperty(name = "spring.ai.model.embedding", havingValue = "ollama")
	public OllamaEmbeddingModel ollamaEmbeddingModel(
		OllamaApi ollamaApi,
		@Value("${spring.ai.ollama.embedding.model}") String model) {
		return OllamaEmbeddingModel.builder()
			.ollamaApi(ollamaApi)
			.options(OllamaEmbeddingOptions.builder()
				.model(model)
				.dimensions(EmbeddingVector.DIMENSIONS)
				.build())
			.build();
	}
}
