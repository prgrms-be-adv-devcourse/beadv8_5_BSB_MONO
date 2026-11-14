package com.bukang.payout.in;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.bukang.payout.app.PayoutCreateUseCase;
import com.bukang.payout.app.PayoutPayUseCase;
import com.bukang.payout.app.PayoutReconcileUseCase;

class DevV1PayoutBatchControllerTest {
	private final PayoutReconcileUseCase payoutReconcileUseCase = mock(PayoutReconcileUseCase.class);
	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DevV1PayoutBatchController(
		mock(PayoutCreateUseCase.class), mock(PayoutPayUseCase.class), payoutReconcileUseCase)).build();

	@Test
	@DisplayName("대사 dev API는 받은 날짜로 대사를 돌린다")
	void reconcileWithDate() throws Exception {
		mockMvc.perform(post("/api/v1/payout/dev/batches/reconcile").param("today", "2026-11-16"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").value("2026-11-16"));

		verify(payoutReconcileUseCase).run(eq(LocalDate.of(2026, 11, 16)), any());
	}
}
