package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.member.app.IdentityCiHasher;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.out.identity.PortOneIdentityClient;
import com.bukang.member.out.identity.PortOneIdentityVerification;
import com.bukang.member.out.identity.VerifiedIdentity;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InMemoryIdentityVerificationStore.Config.class)
class ApiV1IdentityVerificationTest {
	private static final String PATH = "/api/v1/auth/identity-verifications";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private InMemoryIdentityVerificationStore store;

	@Autowired
	private IdentityCiHasher ciHasher;

	// 테스트는 포트원을 부르지 않는다
	@MockitoBean
	private PortOneIdentityClient portOneIdentityClient;

	@AfterEach
	void tearDown() {
		store.clear();
	}

	@Test
	@DisplayName("시작하면 crewrun-iv-로 시작하는 인증 ID를 만들어 대기로 저장한다")
	void startCreatesPendingId() throws Exception {
		String id = start();

		assertThat(id).startsWith("crewrun-iv-");
		assertThat(store.isPending(id)).isTrue();
	}

	@Test
	@DisplayName("포트원 결과가 인증 완료면 확인된 정보를 돌려주고, CI는 해시로만 저장한다")
	void completeSavesVerifiedIdentity() throws Exception {
		String id = start();
		given(portOneIdentityClient.find(id)).willReturn(verified(id, "real-ci-value"));

		complete(id)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.verificationToken").value(id))
			.andExpect(jsonPath("$.data.name").value("김러너"))
			.andExpect(jsonPath("$.data.birthDate").value("1995-04-12"))
			.andExpect(jsonPath("$.data.phone").value("010-1234-5678"));

		VerifiedIdentity saved = store.findVerified(id).orElseThrow();
		assertThat(saved.ciHash()).isEqualTo(ciHasher.hash("real-ci-value")).hasSize(64).doesNotContain("real-ci");
		assertThat(store.isPending(id)).isFalse();
	}

	@Test
	@DisplayName("이미 확인한 ID를 다시 확인하면 포트원을 다시 부르지 않고 저장한 결과를 돌려준다")
	void completeTwiceReturnsSavedResult() throws Exception {
		String id = start();
		given(portOneIdentityClient.find(id)).willReturn(verified(id, "real-ci-value"));

		complete(id).andExpect(status().isOk());
		complete(id).andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("김러너"));

		verify(portOneIdentityClient, times(1)).find(id);
	}

	@Test
	@DisplayName("서버가 만들지 않은 인증 ID는 포트원에 묻지 않고 400 AUTH_VERIFICATION_REQUIRED다")
	void completeFailsWithUnknownId() throws Exception {
		complete("crewrun-iv-made-elsewhere")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AUTH_VERIFICATION_REQUIRED"));

		verify(portOneIdentityClient, times(0)).find("crewrun-iv-made-elsewhere");
	}

	@Test
	@DisplayName("포트원 결과가 인증 완료가 아니면 400 AUTH_VERIFICATION_FAILED이고, 대기가 남아 다시 시도할 수 있다")
	void completeFailsWhenNotVerified() throws Exception {
		String id = start();
		given(portOneIdentityClient.find(id))
			.willReturn(new PortOneIdentityVerification(id, "FAILED", null, null, null, null, null));

		complete(id)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AUTH_VERIFICATION_FAILED"));
		assertThat(store.isPending(id)).isTrue();
	}

	@Test
	@DisplayName("CI를 주지 않는 인증 방법이면 400 AUTH_VERIFICATION_CI_MISSING이다")
	void completeFailsWithoutCi() throws Exception {
		String id = start();
		given(portOneIdentityClient.find(id)).willReturn(verified(id, null));

		complete(id)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AUTH_VERIFICATION_CI_MISSING"));
		assertThat(store.findVerified(id)).isEmpty();
	}

	private String start() throws Exception {
		String body = mockMvc.perform(post(PATH))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.data.identityVerificationId");
	}

	private ResultActions complete(String id) throws Exception {
		return mockMvc.perform(post(PATH + "/" + id + "/complete"));
	}

	private static PortOneIdentityVerification verified(String id, String ci) {
		return new PortOneIdentityVerification(id, "VERIFIED", "김러너", LocalDate.of(1995, 4, 12), "01012345678", ci,
			Instant.parse("2026-11-02T03:00:00Z"));
	}
}
