package com.bukang.ai.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/**
 * 모델 대역. 받은 요청을 남기고 "답변 N"을 돌려준다 (N은 몇 번째 호출인지. 도구를 부르면 그 호출도 센다)
 * 테스트는 모델 답이 아니라, 모델에 무엇을 보냈는지와 Redis에 무엇이 저장됐는지로 확인한다
 */
public class FakeChatModel implements ChatModel {
	private final List<Prompt> prompts = new CopyOnWriteArrayList<>();
	private volatile RuntimeException failure;
	private volatile boolean blankAnswer;
	private volatile String toolToCall;
	private volatile boolean toolForever;

	@Override
	public ChatResponse call(Prompt prompt) {
		if (failure != null) {
			throw failure;
		}
		prompts.add(prompt);
		// 진짜 모델처럼 도구를 불러 달라고 답한다. 도구 실행과 다시 묻기는 Spring AI(ToolCallingAdvisor)가 한다
		boolean toolAnswered = prompt.getInstructions().getLast() instanceof ToolResponseMessage;
		if (toolToCall != null && (toolForever || !toolAnswered)) {
			return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
				.toolCalls(List.of(
					new AssistantMessage.ToolCall("call-" + prompts.size(), "function", toolToCall, "{}")))
				.build())));
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

	// 다음 질문에서 이 도구를 한 번 부른다
	public void callTool(String toolName) {
		this.toolToCall = toolName;
	}

	// 도구 결과를 받아도 계속 같은 도구를 부른다 (호출 한도 확인용)
	public void callToolForever(String toolName) {
		this.toolToCall = toolName;
		this.toolForever = true;
	}

	// 모델에 돌아온 도구 결과 (모델이 본 값)
	public List<String> toolResults() {
		return prompts.stream()
			.filter(prompt -> prompt.getInstructions().getLast() instanceof ToolResponseMessage)
			.flatMap(prompt -> ((ToolResponseMessage)prompt.getInstructions().getLast()).getResponses().stream())
			.map(ToolResponseMessage.ToolResponse::responseData)
			.toList();
	}

	// 마지막 질문에 붙어 모델에 보인 도구의 입력 모양(JSON 스키마)
	public List<String> lastToolInputSchemas() {
		return ((ToolCallingChatOptions)lastPrompt().getOptions()).getToolCallbacks().stream()
			.map(callback -> callback.getToolDefinition().inputSchema())
			.toList();
	}

	// 마지막 질문에 붙어 모델에 보인 도구 이름
	public List<String> lastToolNames() {
		return ((ToolCallingChatOptions)lastPrompt().getOptions()).getToolCallbacks().stream()
			.map(callback -> callback.getToolDefinition().name())
			.toList();
	}

	public void reset() {
		toolToCall = null;
		toolForever = false;
		prompts.clear();
		failure = null;
		blankAnswer = false;
	}
}
