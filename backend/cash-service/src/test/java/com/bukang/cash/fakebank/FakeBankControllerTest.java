package com.bukang.cash.fakebank;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FakeBankControllerTest {
	@Autowired
	private MockMvc mockMvc;

	@AfterEach
	void tearDown() throws Exception {
		changeMode("SUCCESS");
	}

	@ParameterizedTest(name = "{0}: 송금 응답 {1} {2}, 조회 {3}")
	@CsvSource({
		"SUCCESS, 200, COMPLETED, COMPLETED",
		"FAIL, 200, FAILED, FAILED",
		"PENDING_THEN_SUCCESS, 200, PROCESSING, COMPLETED",
		"PENDING_THEN_FAIL, 200, PROCESSING, FAILED",
		"DROP, 504, , COMPLETED"
	})
	@DisplayName("결과 설정에 따라 송금 응답과 이후 조회 결과가 달라진다")
	void transferByMode(String mode, int httpStatus, String transferStatus, String lookupStatus) throws Exception {
		changeMode(mode);
		String transferId = "transfer-" + mode;

		var transfer = mockMvc.perform(post("/fakebank/v1/transfers")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "transferId": "%s", "sellerId": 7, "amount": 30000 }
					""".formatted(transferId)))
			.andExpect(status().is(httpStatus));
		if (transferStatus != null) {
			transfer.andExpect(jsonPath("$.status").value(transferStatus));
		}

		mockMvc.perform(get("/fakebank/v1/transfers/" + transferId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(lookupStatus));
	}

	private void changeMode(String mode) throws Exception {
		mockMvc.perform(put("/fakebank/v1/mode")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "mode": "%s" }
					""".formatted(mode)))
			.andExpect(status().isOk());
	}
}
