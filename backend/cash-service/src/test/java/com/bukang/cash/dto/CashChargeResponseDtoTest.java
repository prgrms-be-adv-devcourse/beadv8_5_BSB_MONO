package com.bukang.cash.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class CashChargeResponseDtoTest {
	@Test
	@DisplayName("사용자 문구는 RsData의 message로만 내보내고, data에는 넣지 않는다")
	void messageIsNotSerializedIntoData() {
		String json = JsonMapper.builder().build().writeValueAsString(CashChargeResponseDto.failed(0, "카드 한도 초과"));

		assertThat(json).isEqualTo("{\"outcome\":\"FAILED\",\"balance\":0}");
	}
}
