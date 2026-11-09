package com.bukang.payout.in;

import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.restdocs.snippet.Snippet;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bukang.payout.domain.PayoutCreateFailure;
import com.bukang.payout.domain.PayoutCreateFailureAction;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.out.MemberAdminPort;
import com.bukang.payout.out.PayoutCreateFailureRepository;
import com.bukang.payout.support.RestDocsConfig;

/**
 * 관리자 「이상 감지」 기록 조회 (BUK-108). 통과한 테스트만 REST Docs 스니펫을 남긴다
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Import(RestDocsConfig.class)
@ActiveProfiles("test")
class ApiV1PayoutAdminControllerTest {
	private static final long ADMIN_ID = 99;
	private static final long MEMBER_ID = 8;

	private static final HeaderDescriptor MEMBER_ID_HEADER =
		headerWithName("X-Member-Id").description("요청한 관리자 회원 ID (게이트웨이가 Access Token에서 꺼내 넣는다)");

	private static final Snippet ERROR_RESPONSE = responseFields(
		fieldWithPath("status").description("HTTP 상태 코드"),
		fieldWithPath("code").description("오류 코드. 코드가 없는 오류는 이 필드가 없다").type(JsonFieldType.STRING).optional(),
		fieldWithPath("message").description("실패 이유"),
		fieldWithPath("data").description("실패 응답에서는 항상 null").type(JsonFieldType.NULL)
	);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PayoutCreateFailureRepository payoutCreateFailureRepository;

	@MockitoBean
	private MemberAdminPort memberAdminPort;

	@BeforeEach
	void setUp() {
		given(memberAdminPort.isAdmin(ADMIN_ID)).willReturn(true);
	}

	@AfterEach
	void tearDown() {
		payoutCreateFailureRepository.deleteAllInBatch();
	}

	// 11/6 실행: 보류 1건, 자동 보정 1건(3장), 주문 조회 실패 1건. 11/5 실행: 다른 날 기록 1건
	private void saveRecords() {
		LocalDate runDate = LocalDate.of(2026, 11, 6);
		payoutCreateFailureRepository.save(PayoutCreateFailure.reconcileMismatch(PayoutStage.최종_정산, 1, 10, null,
			LocalDate.of(2026, 11, 5), "빠진 3장 추가 (코스 3: 3장)", PayoutCreateFailureAction.자동_보정,
			LocalDate.of(2026, 11, 5)));
		payoutCreateFailureRepository.save(PayoutCreateFailure.orderLookup(PayoutStage.일차_정산, 30,
			LocalDate.of(2026, 11, 5), runDate));
		payoutCreateFailureRepository.save(PayoutCreateFailure.reconcileMismatch(PayoutStage.최종_정산, 2, 20, 3L,
			LocalDate.of(2026, 11, 5), "빠진 3장 추가 (코스 3: 3장)", PayoutCreateFailureAction.자동_보정, runDate));
		payoutCreateFailureRepository.save(PayoutCreateFailure.reconcileMismatch(PayoutStage.최종_정산, 1, 10, 4L,
			LocalDate.of(2026, 11, 5), "더 들어감 2장 (코스 4: 2장)", PayoutCreateFailureAction.지급보류, runDate));
	}

	private ResultActions query(long memberId, String from, String to, String type) throws Exception {
		MockHttpServletRequestBuilder request = get("/api/v1/payout/admin/create-failures")
			.header("X-Member-Id", memberId)
			.param("from", from)
			.param("to", to);
		if (type != null) {
			request.param("type", type);
		}
		return mockMvc.perform(request);
	}

	@Test
	@DisplayName("실행일이 기간 안인 기록을 최근 것부터, 카드 숫자와 함께 돌려준다")
	void createFailures() throws Exception {
		saveRecords();

		query(ADMIN_ID, "2026-11-06", "2026-11-06", null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("이상 감지 기록을 조회했습니다."))
			.andExpect(jsonPath("$.data.mismatchCount").value(2))
			.andExpect(jsonPath("$.data.autoFilledCount").value(1))
			.andExpect(jsonPath("$.data.orderLookupFailureCount").value(1))
			.andExpect(jsonPath("$.data.records.totalElements").value(3))
			.andExpect(jsonPath("$.data.records.content[0].failureType").value("대사_불일치"))
			.andExpect(jsonPath("$.data.records.content[0].actionTaken").value("지급보류"))
			.andExpect(jsonPath("$.data.records.content[0].note").value("더 들어감 2장 (코스 4: 2장)"))
			.andExpect(jsonPath("$.data.records.content[0].sellerId").value(1))
			.andExpect(jsonPath("$.data.records.content[0].raceId").value(10))
			.andExpect(jsonPath("$.data.records.content[0].payoutStage").value("최종_정산"))
			.andExpect(jsonPath("$.data.records.content[2].failureType").value("주문_조회_실패"))
			.andExpect(jsonPath("$.data.records.content[2].actionTaken").value("기록만"))
			.andDo(document("payout-admin-create-failures",
				requestHeaders(MEMBER_ID_HEADER),
				queryParameters(
					parameterWithName("from").description("조회 시작 실행일 (yyyy-MM-dd)"),
					parameterWithName("to").description("조회 끝 실행일 (yyyy-MM-dd, 포함). 최대 1개월"),
					parameterWithName("type").description("실패 종류 (예: 대사_불일치, 주문_조회_실패). 없으면 전체").optional(),
					parameterWithName("page").description("페이지 번호 (0부터, 기본 0)").optional(),
					parameterWithName("size").description("페이지 크기 (1 ~ 100, 기본 50)").optional()
				),
				responseFields(
					fieldWithPath("status").description("HTTP 상태 코드"),
					fieldWithPath("message").description("결과 메시지"),
					fieldWithPath("data.mismatchCount").description("카드: 기간 안의 대사 불일치 건수 (종류 선택과 상관없음)"),
					fieldWithPath("data.autoFilledCount")
						.description("카드: 빠진 참가권을 채운 대사 불일치 건수 (판매자 × 대회 줄 수, 장수 아님)"),
					fieldWithPath("data.orderLookupFailureCount").description("카드: 기간 안의 주문 조회 실패 건수"),
					fieldWithPath("data.records.content[].id").description("기록 ID"),
					fieldWithPath("data.records.content[].recordedAt").description("기록 시각"),
					fieldWithPath("data.records.content[].runDate").description("배치 실행일 (기간 조건의 기준)"),
					fieldWithPath("data.records.content[].payoutStage").description("단계 (일차_정산, 최종_정산)"),
					fieldWithPath("data.records.content[].sellerId").description("판매자 ID (대회 · 주문 조회 실패는 null)")
						.optional().type(JsonFieldType.NUMBER),
					fieldWithPath("data.records.content[].raceId").description("대회 ID (대회 조회 실패는 null)")
						.optional().type(JsonFieldType.NUMBER),
					fieldWithPath("data.records.content[].raceCourseId")
						.description("코스 ID (대사 불일치에서 어긋난 코스가 하나일 때만)").optional().type(JsonFieldType.NUMBER),
					fieldWithPath("data.records.content[].payoutTargetDate")
						.description("정산 대상일 (최종은 대회일, 1차는 환불 마감일)").optional().type(JsonFieldType.STRING),
					fieldWithPath("data.records.content[].failureType").description("실패 종류"),
					fieldWithPath("data.records.content[].actionTaken")
						.description("배치가 한 조치 (지급보류, 자동_보정, 기록만). 칸을 더하기 전의 대사 불일치 기록은 null")
						.optional().type(JsonFieldType.STRING),
					fieldWithPath("data.records.content[].note").description("비고 (차이 내용)")
						.optional().type(JsonFieldType.STRING),
					fieldWithPath("data.records.page").description("페이지 번호 (0부터)"),
					fieldWithPath("data.records.size").description("페이지 크기"),
					fieldWithPath("data.records.totalElements").description("전체 기록 수"),
					fieldWithPath("data.records.totalPages").description("전체 페이지 수")
				)));
	}

	@Test
	@DisplayName("기록이 없으면 빈 목록과 카드 0을 돌려준다 (N8)")
	void createFailuresEmpty() throws Exception {
		query(ADMIN_ID, "2026-11-06", "2026-11-06", null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.mismatchCount").value(0))
			.andExpect(jsonPath("$.data.autoFilledCount").value(0))
			.andExpect(jsonPath("$.data.orderLookupFailureCount").value(0))
			.andExpect(jsonPath("$.data.records.content").isEmpty())
			.andDo(document("payout-admin-create-failures-empty"));
	}

	@Test
	@DisplayName("종류를 고르면 목록은 그 종류만, 카드는 기간 전체로 센다")
	void createFailuresByType() throws Exception {
		saveRecords();

		query(ADMIN_ID, "2026-11-01", "2026-11-30", "주문_조회_실패")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.records.totalElements").value(1))
			.andExpect(jsonPath("$.data.records.content[0].raceId").value(30))
			.andExpect(jsonPath("$.data.mismatchCount").value(3));
	}

	@Test
	@DisplayName("관리자가 아니면 403")
	void createFailuresNotAdmin() throws Exception {
		query(MEMBER_ID, "2026-11-06", "2026-11-06", null)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("PAYOUT_NOT_ADMIN"))
			.andDo(document("payout-admin-create-failures-forbidden", ERROR_RESPONSE));
	}

	@Test
	@DisplayName("기간이 1개월 이상이면 400")
	void createFailuresTooLong() throws Exception {
		query(ADMIN_ID, "2026-10-06", "2026-11-06", null)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("이상 감지 기록은 최대 1개월까지 조회할 수 있습니다."))
			.andDo(document("payout-admin-create-failures-too-long", ERROR_RESPONSE));
	}
}
