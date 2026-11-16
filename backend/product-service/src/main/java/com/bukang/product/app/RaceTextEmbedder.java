package com.bukang.product.app;

import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.stereotype.Component;

import com.bukang.product.domain.document.EmbeddingVector;

import lombok.RequiredArgsConstructor;

/**
 * 글 하나를 AI 추천 벡터로 바꾼다 (BUK-162). 대회 벡터를 채우는 임베딩 동기화와, 검색어로 추천하는 AI 추천이 함께 쓴다.
 * 두 곳이 같은 모델·같은 차원으로 바꿔야 벡터끼리 견줄 수 있어서 한곳에 둔다.
 */
@Component
@RequiredArgsConstructor
public class RaceTextEmbedder {
	private final EmbeddingModel embeddingModel;
	// 임베딩을 부를 때 넘기는 옵션 (차원 = EmbeddingVector.DIMENSIONS, AiConfig)
	private final EmbeddingOptions embeddingOptions;

	// 차원을 옵션으로 넘기고, 받은 벡터의 차원이 다르면 예외를 낸다
	// (ES가 저장·검색을 거절하기 전에 원인을 분명히 남긴다. 예: 옵션을 따르지 않는 모델)
	// 임베딩 API가 실패하면 예외가 그대로 나간다. 부른 쪽이 재시도(동기화)하거나 빈 결과(추천)로 바꾼다
	public float[] embed(String text) {
		float[] embedding = embeddingModel.call(new EmbeddingRequest(List.of(text), embeddingOptions))
			.getResult()
			.getOutput();
		if (embedding.length != EmbeddingVector.DIMENSIONS) {
			throw new IllegalStateException("임베딩 벡터의 차원이 " + embedding.length + "입니다. "
				+ EmbeddingVector.DIMENSIONS + "이어야 합니다.");
		}
		return embedding;
	}
}
