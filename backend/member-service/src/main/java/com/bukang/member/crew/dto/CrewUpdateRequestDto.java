package com.bukang.member.crew.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 이름 검사(앞뒤 공백을 뺀 1~20자)는 만들기와 같이 Crew에서 한다 (CrewCreateRequestDto 참고).
// 그래서 컨트롤러에 @Valid가 없다. 여기에 검증 애노테이션을 붙이면 @Valid도 함께 붙인다
@Schema(description = "크루 이름 바꾸기 요청")
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class CrewUpdateRequestDto {
	@Schema(description = "새 크루 이름 (앞뒤 공백을 뺀 1~20자, 다른 크루와 겹쳐도 된다)", example = "새벽한강러닝크루",
		requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 20)
	private String name;
}
