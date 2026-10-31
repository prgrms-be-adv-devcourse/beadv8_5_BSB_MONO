package com.bukang.payout.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
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
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.restdocs.snippet.Snippet;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.payout.domain.PayoutClaim;
import com.bukang.payout.domain.PayoutClaimStatus;
import com.bukang.payout.out.MemberAdminPort;
import com.bukang.payout.out.PayoutClaimRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.support.RestDocsConfig;

// 통과한 테스트만 REST Docs 스니펫(build/generated-snippets)을 남긴다. 문서는 src/docs/asciidoc/index.adoc에서 묶는다
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Import(RestDocsConfig.class)
@ActiveProfiles("test")
class ApiV1PayoutClaimControllerTest {
	private static final long ADMIN_ID = 99;
	private static final long MEMBER_ID = 8;

	private static final Snippet ERROR_RESPONSE = responseFields(
		fieldWithPath("status").description("HTTP 상태 코드"),
		fieldWithPath("code").description("오류 코드. 코드가 없는 오류는 이 필드가 없다")
			.type(JsonFieldType.STRING).optional(),
		fieldWithPath("message").description("실패 이유"),
		fieldWithPath("data").description("실패 응답에서는 항상 null").type(JsonFieldType.NULL)
	);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PayoutClaimRepository payoutClaimRepository;

	@Autowired
	private PayoutItemRepository payoutItemRepository;

	// 회원 서비스 대신 관리자 여부를 정한다. 정하지 않은 회원은 일반 회원(false)이다
	@MockitoBean
	private MemberAdminPort memberAdminPort;

	@BeforeEach
	void setUp() {
		given(memberAdminPort.isAdmin(ADMIN_ID)).willReturn(true);
	}

	@AfterEach
	void tearDown() {
		payoutItemRepository.deleteAll();
		payoutClaimRepository.deleteAll();
	}

	private PayoutClaim saveClaim() {
		return payoutClaimRepository.save(new PayoutClaim(1, 7, 105_500, LocalDate.of(2026, 12, 14)));
	}

	private ResultActions confirm(long claimId, long memberId) throws Exception {
		return mockMvc.perform(post("/api/v1/payout/claims/{claimId}/confirm", claimId)
			.header("X-Member-Id", memberId));
	}

	@Test
	@DisplayName("청구 입금을 확인하면 200을 돌려주고, 입금 확인 상태와 확인한 관리자가 남는다")
	void confirmClaim() throws Exception {
		PayoutClaim claim = saveClaim();

		confirm(claim.getId(), ADMIN_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("청구 입금을 확인했습니다."))
			.andExpect(jsonPath("$.data.id").value(claim.getId()))
			.andExpect(jsonPath("$.data.amount").value(105_500))
			.andExpect(jsonPath("$.data.status").value("CONFIRMED"))
			.andExpect(jsonPath("$.data.confirmedBy").value(ADMIN_ID))
			.andDo(document("payout-claim-confirm",
				requestHeaders(headerWithName("X-Member-Id").description("입금을 확인한 관리자 회원 ID (게이트웨이가 토큰에서 꺼내 넣는다)")),
				pathParameters(parameterWithName("claimId").description("입금을 확인할 청구 ID")),
				responseFields(
					fieldWithPath("status").description("HTTP 상태 코드"),
					fieldWithPath("message").description("결과 메시지"),
					fieldWithPath("data.id").description("청구 ID"),
					fieldWithPath("data.sellerId").description("돈을 돌려줄 판매자 ID"),
					fieldWithPath("data.raceId").description("대회 ID"),
					fieldWithPath("data.amount").description("청구 금액 (최종 정산 상계 뒤 남은 갚을 돈)"),
					fieldWithPath("data.startDate").description("상계 실패일 (최종 정산을 만든 날, 대회 취소는 취소일)"),
					fieldWithPath("data.status").description("청구 상태 (OPEN: 청구 중, CONFIRMED: 입금 확인)"),
					fieldWithPath("data.confirmedBy").description("입금을 확인한 관리자 회원 ID (확인 전에는 null)")
						.optional().type(JsonFieldType.NUMBER),
					fieldWithPath("data.confirmedAt").description("입금 확인 일시 (확인 전에는 null)")
						.optional().type(JsonFieldType.STRING)
				)));

		assertThat(payoutClaimRepository.findById(claim.getId()).orElseThrow().getStatus())
			.isEqualTo(PayoutClaimStatus.CONFIRMED);
	}

	@Test
	@DisplayName("없는 청구를 확인하면 404")
	void notFound() throws Exception {
		confirm(9999, ADMIN_ID)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("청구를 찾을 수 없습니다."))
			.andDo(document("payout-claim-confirm-not-found", ERROR_RESPONSE));
	}

	@Test
	@DisplayName("TC-CONFIRM-4-1 이미 입금을 확인한 청구를 다시 확인하면 409")
	void alreadyConfirmed() throws Exception {
		PayoutClaim claim = saveClaim();
		confirm(claim.getId(), ADMIN_ID).andExpect(status().isOk());

		confirm(claim.getId(), ADMIN_ID)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 입금을 확인한 청구입니다."))
			.andDo(document("payout-claim-confirm-conflict", ERROR_RESPONSE));
	}

	@Test
	@DisplayName("TC-CONFIRM-4-2 관리자가 아닌 회원이 청구 입금을 확인하면 403이고, 청구와 원장은 그대로다")
	void nonAdminCannotConfirm() throws Exception {
		PayoutClaim claim = saveClaim();

		confirm(claim.getId(), MEMBER_ID)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("PAYOUT_NOT_ADMIN"))
			.andDo(document("payout-claim-confirm-forbidden", ERROR_RESPONSE));
		assertThat(payoutClaimRepository.findById(claim.getId()).orElseThrow().getStatus())
			.isEqualTo(PayoutClaimStatus.OPEN);
		assertThat(payoutItemRepository.count()).isZero();
	}
}
