package com.bukang.product.domain.document;

/**
 * AI 추천 임베딩 벡터 (BUK-162)
 * 임베딩 모델 하나가 만드는 벡터의 숫자 개수라, 벡터 칸(dense_vector)을 가진 모든 문서가 같은 값을 쓴다.
 * 차원의 원본은 이 값 하나다. yaml에는 적지 않고, AiConfig의 Ollama 빈과 임베딩을 부를 때 넘기는 옵션(embeddingOptions)이 이 값을 쓴다.
 * 바꾸면 이미 만든 인덱스의 매핑과 저장된 벡터를 모두 다시 만들어야 한다 (dense_vector 차원은 바꿀 수 없다)
 */
public final class EmbeddingVector {
	public static final int DIMENSIONS = 384;

	private EmbeddingVector() {
	}
}
