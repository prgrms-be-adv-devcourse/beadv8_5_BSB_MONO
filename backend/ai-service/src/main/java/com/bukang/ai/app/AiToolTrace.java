package com.bukang.ai.app;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.ai.chat.model.ToolContext;

import com.bukang.ai.domain.AiSource;
import com.bukang.ai.dto.AiListDto;
import com.bukang.ai.dto.AiSourceDto;

import lombok.extern.slf4j.Slf4j;

/**
 * 질문 하나를 처리하는 동안 도구에 넘기는 값(ToolContext)이자, 도구가 무엇을 불렀는지 남기는 기록
 * - 회원 번호는 로그인 정보에서 온 값이다. 모델은 이 값을 보지도 정하지도 못한다
 * - 출처와 목록은 여기 모았다가 응답 data에 붙인다. 목록(구매자 이름)은 모델에 보내지 않는다
 */
@Slf4j
public class AiToolTrace {
	public static final String KEY = "trace";
	public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	private final long memberId;
	private final Map<AiSource, AiSourceDto> sources = new LinkedHashMap<>();
	private AiListDto list;

	public AiToolTrace(long memberId) {
		this.memberId = memberId;
	}

	public static AiToolTrace of(ToolContext toolContext) {
		return (AiToolTrace)toolContext.getContext().get(KEY);
	}

	public long memberId() {
		return memberId;
	}

	/**
	 * 조회하고 출처를 남긴다. 실패하면 예외를 모델 쪽으로 던지지 않고, 숫자 없이 화면에서 확인하라는 결과를 돌려준다
	 * (출처는 조회 시각 없이 남겨 화면이 버튼만 보여 주게 한다)
	 */
	public synchronized Object run(AiSource source, Supplier<Object> query) {
		try {
			Object result = query.get();
			sources.put(source, AiSourceDto.of(source, LocalDateTime.now(ZONE).withNano(0)));
			return result;
		} catch (RuntimeException exception) {
			log.warn("도구가 값을 가져오지 못했습니다. source={}, memberId={}", source, memberId, exception);
			sources.put(source, AiSourceDto.of(source, null));
			return Map.of("error", "지금은 조회하지 못했다. 숫자를 말하지 말고 '%s' 화면에서 확인하라고 안내한다."
				.formatted(source.getScreen()));
		}
	}

	public synchronized void list(List<AiListDto.Row> rows, long moreCount) {
		this.list = new AiListDto(rows, moreCount);
	}

	public synchronized List<AiSourceDto> sources() {
		return List.copyOf(sources.values());
	}

	public synchronized AiListDto list() {
		return list;
	}
}
