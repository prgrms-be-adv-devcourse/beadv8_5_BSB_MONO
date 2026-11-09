package com.bukang.product.dto;

import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.bukang.product.domain.KitOption;

/**
 * 키트 옵션 목록 응답이 옵션을 이름별로 묶는지(KitOptionListResponseDto.of) 확인한다.
 */
class KitOptionListResponseDtoTest {
	@Test
	@DisplayName("옵션 ID 순으로 [T-shirt M, socks 230(맞춤 제작), T-shirt L]이면 T-shirt [M, L], socks [230] 순서로 묶고 "
		+ "맞춤 제작의 준비 수량은 null이다")
	void groupByNameInFirstAppearanceOrder() {
		// given
		List<KitOption> options = List.of(
			option(11, "T-shirt", "M", 100),
			option(12, "socks", "230", null),
			option(13, "T-shirt", "L", 80));

		// when
		KitOptionListResponseDto response = KitOptionListResponseDto.of(1, options);

		// then
		assertSoftly(softly -> {
			softly.assertThat(response.getRaceId()).isEqualTo(1);
			softly.assertThat(response.getKitOptions())
				.extracting(KitOptionListResponseDto.Group::name)
				.containsExactly("T-shirt", "socks");
			softly.assertThat(response.getKitOptions().get(0).values())
				.extracting(KitOptionListResponseDto.Value::kitOptionId, KitOptionListResponseDto.Value::value,
					KitOptionListResponseDto.Value::stock)
				.containsExactly(tuple(11L, "M", 100), tuple(13L, "L", 80));
			softly.assertThat(response.getKitOptions().get(1).values())
				.extracting(KitOptionListResponseDto.Value::kitOptionId, KitOptionListResponseDto.Value::value,
					KitOptionListResponseDto.Value::stock)
				.containsExactly(tuple(12L, "230", null));
		});
	}

	// DB에 저장한 옵션처럼 ID를 채운다 (ID는 저장할 때 DB가 만들어 생성자로 넣을 수 없다). 묶기는 대회를 보지 않는다
	private static KitOption option(long id, String name, String value, Integer stock) {
		KitOption option = new KitOption(null, name, value, stock);
		ReflectionTestUtils.setField(option, "id", id);
		return option;
	}
}
