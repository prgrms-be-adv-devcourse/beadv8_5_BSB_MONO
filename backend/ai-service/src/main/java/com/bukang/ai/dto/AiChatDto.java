package com.bukang.ai.dto;

import java.util.List;

// sources는 부른 도구 순서대로(도구를 부르지 않았으면 빈 목록), list는 목록이 필요한 답일 때만 있고 아니면 null
public record AiChatDto(String answer, List<AiSourceDto> sources, AiListDto list) {
}
