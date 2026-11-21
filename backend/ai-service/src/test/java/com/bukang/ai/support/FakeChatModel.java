package com.bukang.ai.support;

import java.util.List;
import java.util.Map;
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
 * 모델 대역. 받은 요청을 남기고 "답변 가", "답변 나"처럼 몇 번째 호출인지 글자로 돌려준다 (도구를 부르면 그 호출도 센다)
 * 숫자로 쓰지 않는다. 도구 없이 숫자를 말한 답은 AiChatUseCase가 버리고 다시 묻기 때문이다
 * 테스트는 모델 답이 아니라, 모델에 무엇을 보냈는지와 Redis에 무엇이 저장됐는지로 확인한다
 */
public class FakeChatModel implements ChatModel {
	private static final String ORDER = "가나다라마바사아자차카타파하";
	private final List<Prompt> prompts = new CopyOnWriteArrayList<>();
	private volatile RuntimeException failure;
	private volatile boolean blankAnswer;
	private volatile String toolToCall;
	private volatile String toolArguments = "{}";
	private volatile boolean toolForever;
	private volatile String fixedAnswer;
	private final List<String> answersInOrder = new CopyOnWriteArrayList<>();

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
					new AssistantMessage.ToolCall("call-" + prompts.size(), "function", toolToCall, toolArguments)))
				.build())));
		}
		String text = !answersInOrder.isEmpty() ? answersInOrder.removeFirst()
			: fixedAnswer != null ? fixedAnswer : blankAnswer ? " " : "답변 " + ORDER.charAt(prompts.size() - 1);
		// OpenAI의 답처럼 문자열이 아닌 메타데이터(숫자, 목록)를 단다. 이런 답도 대화 기록에서 다시 읽혀야 한다
		return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content(text)
			.properties(Map.of("index", 0, "annotations", List.of(), "finishReason", "STOP"))
			.build())));
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

	// 정해 둔 글로 답한다 (문의 초안처럼 JSON 모양 답이 필요할 때)
	public void answerWith(String text) {
		this.fixedAnswer = text;
	}

	// 부를 때마다 차례로 이 글로 답한다. 다 쓰면 원래대로 답한다
	public void answerInOrder(String... texts) {
		answersInOrder.addAll(List.of(texts));
	}

	// 다음 질문에서 이 도구를 한 번 부른다
	public void callTool(String toolName) {
		this.toolToCall = toolName;
	}

	// 다음 질문에서 이 도구를 이 인자(JSON)로 한 번 부른다 (정책 도구의 주제)
	public void callTool(String toolName, String arguments) {
		this.toolToCall = toolName;
		this.toolArguments = arguments;
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
		toolArguments = "{}";
		toolForever = false;
		prompts.clear();
		failure = null;
		blankAnswer = false;
		fixedAnswer = null;
		answersInOrder.clear();
	}
}
