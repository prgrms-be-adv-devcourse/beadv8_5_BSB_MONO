package com.bukang.payout.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.YearMonth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.restdocs.snippet.Snippet;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.support.RestDocsConfig;
import com.jayway.jsonpath.JsonPath;

// 통과한 테스트만 REST Docs 스니펫(build/generated-snippets)을 남긴다. 문서는 src/docs/asciidoc/index.adoc에서 묶는다
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Import(RestDocsConfig.class)
@ActiveProfiles("test")
class ApiV1PayoutHoldControllerTest {
	private static final int ADMIN_ID = 99;

	private static final HeaderDescriptor MEMBER_ID_HEADER =
		headerWithName("X-Member-Id").description("요청한 관리자 회원 ID (게이트웨이가 넣어 줄 예정)");

	// 성공·실패 응답 모두 RsData {status, message, data} 형식이다
	private static final FieldDescriptor[] HOLD_RESPONSE = {
		fieldWithPath("status").description("HTTP 상태 코드"),
		fieldWithPath("message").description("결과 메시지"),
		fieldWithPath("data.id").description("지급보류 ID (풀기 API 경로에 쓴다)"),
		fieldWithPath("data.holdCode").description("지급보류 ID 표기 (HD-보류한 날-16진수 4자리)"),
		fieldWithPath("data.payoutId").description("보류된 정산 내역 ID"),
		fieldWithPath("data.reasonType").description("보류 사유 (ACCOUNT_NOT_REGISTERED, RACE_CANCELED)"),
		fieldWithPath("data.reason").description("사유 상세").optional().type(JsonFieldType.STRING),
		fieldWithPath("data.status").description("보류 상태 (HELD: 지급보류, RELEASED: 해제)"),
		fieldWithPath("data.heldBy").description("보류한 관리자 회원 ID"),
		fieldWithPath("data.heldAt").description("보류 일시"),
		fieldWithPath("data.releasedBy").description("푼 관리자 회원 ID (보류 중에는 null)")
			.optional().type(JsonFieldType.NUMBER),
		fieldWithPath("data.releasedAt").description("해제 일시 (보류 중에는 null)")
			.optional().type(JsonFieldType.STRING)
	};

	private static final Snippet ERROR_RESPONSE = responseFields(
		fieldWithPath("status").description("HTTP 상태 코드"),
		fieldWithPath("code").description("오류 코드 (예: 입력값 오류는 INVALID_REQUEST). 코드가 없는 오류는 이 필드가 없다")
			.type(JsonFieldType.STRING).optional(),
		fieldWithPath("message").description("실패 이유"),
		fieldWithPath("data").description("실패 응답에서는 항상 null").type(JsonFieldType.NULL)
	);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutHoldRepository payoutHoldRepository;

	@AfterEach
	void tearDown() {
		payoutHoldRepository.deleteAll();
		payoutRepository.deleteAll();
	}

	private Payout savePayout() {
		return payoutRepository.save(Payout.finalStage(1, 10, "테스트 대회", YearMonth.of(2026, 9), 0L,
			LocalDateTime.of(2026, 9, 29, 3, 0), new BusinessDayCalendar()));
	}

	private ResultActions hold(int payoutId, String body) throws Exception {
		return mockMvc.perform(post("/api/v1/payout/payouts/{payoutId}/holds", payoutId)
			.header("X-Member-Id", ADMIN_ID)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions holdAccount(int payoutId) throws Exception {
		return hold(payoutId, """
			{"reasonType": "ACCOUNT_NOT_REGISTERED", "reason": "계좌 확인 중"}
			""");
	}

	private ResultActions release(int holdId) throws Exception {
		return mockMvc.perform(post("/api/v1/payout/holds/{holdId}/release", holdId)
			.header("X-Member-Id", ADMIN_ID));
	}

	private int holdIdOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id");
	}

	private PayoutStatus statusOf(Payout payout) {
		return payoutRepository.findById(payout.getId()).orElseThrow().getStatus();
	}

	@Test
	@DisplayName("보류를 걸면 201과 지급보류 ID를 돌려주고, 정산 내역은 HELD가 된다")
	void holdPayout() throws Exception {
		Payout payout = savePayout();

		ResultActions result = holdAccount(payout.getId())
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value(201))
			.andExpect(jsonPath("$.message").value("지급보류를 걸었습니다."))
			.andExpect(jsonPath("$.data.holdCode").value(startsWith("HD-")))
			.andExpect(jsonPath("$.data.payoutId").value(payout.getId()))
			.andExpect(jsonPath("$.data.reasonType").value("ACCOUNT_NOT_REGISTERED"))
			.andExpect(jsonPath("$.data.status").value("HELD"))
			.andExpect(jsonPath("$.data.heldBy").value(ADMIN_ID))
			.andDo(document("payout-hold-create",
				requestHeaders(MEMBER_ID_HEADER),
				pathParameters(parameterWithName("payoutId").description("보류를 걸 정산 내역 ID")),
				requestFields(
					fieldWithPath("reasonType").description("보류 사유 (ACCOUNT_NOT_REGISTERED: 정산 계좌 미등록, "
						+ "RACE_CANCELED: 대회 취소 처리)"),
					fieldWithPath("reason").description("사유 상세 (선택, 500자 이하)").optional()
				),
				responseHeaders(headerWithName("Location").description("만든 지급보류의 경로")),
				responseFields(HOLD_RESPONSE)));

		result.andExpect(header().string("Location", "/api/v1/payout/holds/" + holdIdOf(result)));
		assertThat(statusOf(payout)).isEqualTo(PayoutStatus.HELD);
	}

	@Test
	@DisplayName("보류를 풀면 200을 돌려주고, 정산 내역은 CALCULATED로 돌아간다")
	void releaseHold() throws Exception {
		Payout payout = savePayout();
		int holdId = holdIdOf(holdAccount(payout.getId()));

		release(holdId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("지급보류를 풀었습니다."))
			.andExpect(jsonPath("$.data.status").value("RELEASED"))
			.andExpect(jsonPath("$.data.releasedBy").value(ADMIN_ID))
			.andExpect(jsonPath("$.data.releasedAt").isNotEmpty())
			.andDo(document("payout-hold-release",
				requestHeaders(MEMBER_ID_HEADER),
				pathParameters(parameterWithName("holdId").description("풀 지급보류 ID (보류 걸기 응답의 data.id)")),
				responseFields(HOLD_RESPONSE)));

		assertThat(statusOf(payout)).isEqualTo(PayoutStatus.CALCULATED);
	}

	@Test
	@DisplayName("이미 보류 중인 정산 내역에 다시 걸면 409")
	void holdTwice() throws Exception {
		Payout payout = savePayout();
		holdAccount(payout.getId());

		holdAccount(payout.getId())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 지급보류 중인 정산 내역입니다."))
			.andDo(document("payout-hold-create-conflict", ERROR_RESPONSE));
	}

	@Test
	@DisplayName("이미 해제된 보류를 다시 풀면 409")
	void releaseTwice() throws Exception {
		Payout payout = savePayout();
		int holdId = holdIdOf(holdAccount(payout.getId()));
		release(holdId);

		release(holdId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 해제된 지급보류입니다."))
			.andDo(document("payout-hold-release-conflict", ERROR_RESPONSE));
	}

	@Test
	@DisplayName("없는 정산 내역, 없는 보류는 404")
	void notFound() throws Exception {
		holdAccount(9999)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("정산 내역을 찾을 수 없습니다."))
			.andDo(document("payout-hold-create-not-found", ERROR_RESPONSE));
		release(9999)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("지급보류를 찾을 수 없습니다."))
			.andDo(document("payout-hold-release-not-found", ERROR_RESPONSE));
	}

	@Test
	@DisplayName("회원 ID 헤더가 없으면 401")
	void missingMemberId() throws Exception {
		mockMvc.perform(post("/api/v1/payout/holds/{holdId}/release", 1))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
			.andDo(document("payout-hold-unauthorized", ERROR_RESPONSE));
	}

	@Test
	@DisplayName("보류 사유가 없거나 없는 사유 값이면 400")
	void invalidReason() throws Exception {
		Payout payout = savePayout();

		hold(payout.getId(), "{}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("보류 사유를 선택해주세요."))
			.andDo(document("payout-hold-create-bad-request", ERROR_RESPONSE));
		hold(payout.getId(), """
			{"reasonType": "UNKNOWN"}
			""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("요청 본문 형식이 올바르지 않습니다."));
		assertThat(statusOf(payout)).isEqualTo(PayoutStatus.CALCULATED);
	}

	@Test
	@DisplayName("경로의 ID가 숫자가 아니면 400")
	void invalidPathId() throws Exception {
		mockMvc.perform(post("/api/v1/payout/holds/{holdId}/release", "abc")
				.header("X-Member-Id", ADMIN_ID))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다."));
	}
}
