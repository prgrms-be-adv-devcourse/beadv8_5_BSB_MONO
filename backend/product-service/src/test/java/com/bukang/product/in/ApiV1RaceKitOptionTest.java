package com.bukang.product.in;

import static com.bukang.product.support.RaceRequests.race;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.product.domain.KitOption;
import com.bukang.product.dto.KitOptionSaveRequestDto;
import com.bukang.product.dto.KitOptionsSaveRequestDto;
import com.bukang.product.support.ProductIntegrationTest;
import com.bukang.product.support.RaceRequests.RaceBuilder;

/**
 * 키트 옵션 등록·수정(POST·PUT /api/v1/product/races/{raceId}/kit-options)을 확인한다 (BUK-137)
 * - 대회는 키트 옵션 없이 등록하고, 키트 옵션은 이 API로 따로 붙인다
 * - 판매 시작 전에만 옵션을 등록·추가·제거할 수 있고, 판매 시작 뒤에는 준비 수량 늘리기만 된다 (11/09 결정)
 */
class ApiV1RaceKitOptionTest extends ProductIntegrationTest {
	private static final String RACES = "/api/v1/product/races/";

	@Test
	@DisplayName("키트 옵션이 없는 대회에 T-shirt M·L을 등록하면 201이고, 목록 조회에 등록한 순서대로 보인다")
	void registerKitOptions() throws Exception {
		// given
		RegisteredRace scheduled = register(scheduledRace().build());

		// when
		ResultActions registered = postJson(kitOptionsPath(scheduled.raceId()), SELLER_MEMBER_ID,
			kitOptions(option("T-shirt", "M", 100), option("T-shirt", "L", 80)));

		// then
		registered.andExpect(status().isCreated())
			.andExpect(jsonPath("$.message").value("키트 옵션을 등록했습니다."));
		getAs(kitOptionsPath(scheduled.raceId()), null)
			.andExpect(jsonPath("$.data.kitOptions[0].name").value("T-shirt"))
			.andExpect(jsonPath("$.data.kitOptions[0].values[*].value", contains("M", "L")));
	}

	@Test
	@DisplayName("키트 옵션 이름을 T-shirt와 t-shirt처럼 대소문자만 다르게 써서 등록하면 400이고 아무것도 저장하지 않는다")
	void rejectKitOptionNameWithDifferentCase() throws Exception {
		// given
		RegisteredRace scheduled = register(scheduledRace().build());

		// when
		ResultActions registered = postJson(kitOptionsPath(scheduled.raceId()), SELLER_MEMBER_ID,
			kitOptions(option("T-shirt", "M", 100), option("t-shirt", "L", 50)));

		// then
		registered.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("같은 키트 옵션 이름은 대소문자까지 똑같이 작성해주세요. (T-shirt, t-shirt)"));
		assertThat(savedOptions(scheduled.raceId())).isEmpty();
	}

	@Test
	@DisplayName("판매가 시작된 대회에서 준비 수량 100인 옵션을 150으로 늘리면 200이고 150이 된다")
	void increaseStockAfterSaleStart() throws Exception {
		// given
		RegisteredRace onSale = registerOnSale(race().build());
		saveKitOption(onSale.raceId(), "T-shirt", "L", 100);

		// when
		ResultActions updated = putJson(kitOptionsPath(onSale.raceId()), SELLER_MEMBER_ID,
			kitOptions(option("T-shirt", "L", 150)));

		// then
		updated.andExpect(status().isOk());
		assertThat(savedOptions(onSale.raceId()))
			.extracting(KitOption::getValue, KitOption::getStock)
			.containsExactly(tuple("L", 150));
	}

	@Test
	@DisplayName("판매가 시작된 대회에 새 옵션 T-shirt XL을 더하면 409이고 옵션은 그대로다")
	void rejectAddingKitOptionAfterSaleStart() throws Exception {
		// given
		RegisteredRace onSale = registerOnSale(race().build());
		saveKitOption(onSale.raceId(), "T-shirt", "L", 100);

		// when
		ResultActions updated = putJson(kitOptionsPath(onSale.raceId()), SELLER_MEMBER_ID,
			kitOptions(option("T-shirt", "L", 100), option("T-shirt", "XL", 50)));

		// then
		updated.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("판매가 시작된 대회는 키트 옵션을 더할 수 없습니다. (옵션: T-shirt XL)"));
		assertThat(savedOptions(onSale.raceId()))
			.extracting(KitOption::getValue)
			.containsExactly("L");
	}

	// 판매 시작일 전인 판매 예정 대회 (개인 예매 이틀 뒤 오픈. 판매 시작일은 오늘로부터 이틀 뒤부터 정할 수 있다)
	private static RaceBuilder scheduledRace() {
		RaceBuilder builder = race();
		return builder.individualOpenAt(builder.now().plusDays(2));
	}

	private static KitOptionsSaveRequestDto kitOptions(KitOptionSaveRequestDto... options) {
		return new KitOptionsSaveRequestDto(List.of(options));
	}

	private static KitOptionSaveRequestDto option(String name, String value, Integer stock) {
		return new KitOptionSaveRequestDto(name, value, stock);
	}

	private static String kitOptionsPath(long raceId) {
		return RACES + raceId + "/kit-options";
	}

	private List<KitOption> savedOptions(long raceId) {
		return kitOptionRepository.findByRaceIdOrderById(raceId);
	}
}
