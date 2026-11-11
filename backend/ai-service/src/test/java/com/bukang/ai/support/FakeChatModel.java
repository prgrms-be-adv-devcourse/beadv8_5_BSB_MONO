package com.bukang.ai.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 모델 대역. 받은 요청을 남기고 "답변 N"을 돌려준다 (N은 몇 번째 호출인지)
 * 테스트는 모델 답이 아니라, 모델에 무엇을 보냈는지와 Redis에 무엇이 저장됐는지로 확인한다
 */
public class FakeChatModel implements ChatModel {
	private final List<Prompt> prompts = new CopyOnWriteArrayList<>();
	private volatile RuntimeException failure;

	@Override
	public ChatResponse call(Prompt prompt) {
		if (failure != null) {
			throw failure;
		}
		prompts.add(prompt);
		return new ChatResponse(List.of(new Generation(new AssistantMessage("답변 " + prompts.size()))));
	}

	public List<Prompt> prompts() {
		return prompts;
	}

	public Prompt lastPrompt() {
		return prompts.getLast();
	}

	public void failWith(RuntimeException failure) {
		this.failure = failure;
	}

	public void reset() {
		prompts.clear();
		failure = null;
	}
}
