package com.bukang.ai.dto;

import java.util.List;

// 답 아래 목록 카드 (시안 1388:65090). 모델을 거치지 않고 조회한 값을 그대로 담는다. moreCount는 목록에 담지 않은 나머지 건수
public record AiListDto(List<Row> rows, long moreCount) {
	public record Row(String orderNo, String name, String detail) {
	}
}
