package com.bukang.ai.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/**
 * 모델 대역. 받은 요청을 남기고 "답변 N"을 돌려준다 (N은 몇 번째 호출인지)
 * 테스트는 모델 답이 아니라, 모델에 무엇을 보냈는지와 Redis에 무엇이 저장됐는지로 확인한다
 */
public class FakeChatModel implements ChatModel {
	private final List<Prompt> prompts = new CopyOnWriteArrayList<>();
	private volatile RuntimeException failure;
	private volatile boolean blankAnswer;
	private volatile String toolToCall;
	private final List<String> toolResults = new CopyOnWriteArrayList<>();

	@Override
	public ChatResponse call(Prompt prompt) {
		if (failure != null) {
			throw failure;
		}
		prompts.add(prompt);
		// 진짜 모델이 도구를 고른 것처럼, 이 질문에 붙은 도구 중 이름이 같은 것을 부른다 (붙지 않은 도구는 부를 수 없다)
		if (toolToCall != null && prompt.getOptions() instanceof ToolCallingChatOptions options) {
			options.getToolCallbacks().stream()
				.filter(callback -> callback.getToolDefinition().name().equals(toolToCall))
				.forEach(callback -> toolResults.add(callback.call("{}", new ToolContext(options.getToolContext()))));
		}
		String text = blankAnswer ? " " : "답변 " + prompts.size();
		return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
	}

	// 진짜 모델(OpenAI·Ollama)처럼 도구를 실을 수 있는 옵션을 쓴다. 기본 옵션이면 ChatClient가 도구를 요청에 싣지 않는다
	@Override
	public ChatOptions getOptions() {
		return ToolCallingChatOptions.builder().build();
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

	public void answerBlank() {
		this.blankAnswer = true;
	}

	public void callTool(String toolName) {
		this.toolToCall = toolName;
	}

	public List<String> toolResults() {
		return toolResults;
	}

	// 마지막 질문에 붙어 모델에 보인 도구 이름
	public List<String> lastToolNames() {
		return ((ToolCallingChatOptions)lastPrompt().getOptions()).getToolCallbacks().stream()
			.map(callback -> callback.getToolDefinition().name())
			.toList();
	}

	public void reset() {
		toolToCall = null;
		toolResults.clear();
		prompts.clear();
		failure = null;
		blankAnswer = false;
	}
}
