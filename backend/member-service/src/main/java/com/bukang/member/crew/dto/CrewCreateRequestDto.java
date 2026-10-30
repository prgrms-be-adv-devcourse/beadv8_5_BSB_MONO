package com.bukang.member.crew.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 칸이 하나라 생성자 하나로 받으면 JSON 객체가 아니라 값 자체로 읽힐 수 있어, 기본 생성자 + 필드로 받는다
// 이름 검사(앞뒤 공백을 뺀 1~20자)는 공백을 뺀 값으로 해야 해서 Crew.create에서 한다
@Schema(description = "크루 만들기 요청")
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class CrewCreateRequestDto {
	@Schema(description = "크루 이름 (앞뒤 공백을 뺀 1~20자, 다른 크루와 겹쳐도 된다)", example = "새벽한강크루",
		requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 20)
	private String name;
}
