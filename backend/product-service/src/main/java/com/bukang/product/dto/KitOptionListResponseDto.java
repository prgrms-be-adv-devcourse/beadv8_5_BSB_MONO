package com.bukang.product.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.bukang.product.domain.KitOption;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 대회의 키트 옵션 목록. 프론트는 이름(name)마다 선택 상자 하나를 그리고, 구매자는 이름마다 값 하나를 고른다
@AllArgsConstructor
@Getter
public class KitOptionListResponseDto {
	private final long raceId;
	private final List<Group> kitOptions;

	// options는 옵션 ID 순(등록 순)이어야 한다. 이름은 처음 나온 순서대로, 같은 이름의 값도 그 순서대로 묶는다
	//   예: [T-shirt M, shoes 240, T-shirt L] → T-shirt [M, L], shoes [240]
	public static KitOptionListResponseDto of(long raceId, List<KitOption> options) {
		Map<String, List<Value>> valuesByName = new LinkedHashMap<>();
		for (KitOption option : options) {
			valuesByName.computeIfAbsent(option.getName(), name -> new ArrayList<>())
				.add(new Value(option.getId(), option.getValue(), option.getStock()));
		}
		List<Group> groups = valuesByName.entrySet().stream()
			.map(entry -> new Group(entry.getKey(), entry.getValue()))
			.toList();
		return new KitOptionListResponseDto(raceId, groups);
	}

	// 같은 이름의 옵션 묶음 (예: T-shirt → M, L)
	public record Group(String name, List<Value> values) {
	}

	// 옵션 하나. stock이 null이면 맞춤 제작이라 품절이 없다. JSON에도 칸을 빼지 않고 null로 내려 준다
	// 품절인지는 주문 서비스가 판매 수와 견주어 판단한다 (상품은 준비 수량만 안다)
	public record Value(long kitOptionId, String value, Integer stock) {
	}
}
