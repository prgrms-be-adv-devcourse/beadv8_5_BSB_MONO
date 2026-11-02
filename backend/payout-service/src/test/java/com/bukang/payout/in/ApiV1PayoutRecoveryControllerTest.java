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

import java.time.LocalDateTime;

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

import com.bukang.payout.domain.PayoutRecovery;
import com.bukang.payout.domain.PayoutRecoveryReason;
import com.bukang.payout.domain.PayoutRecoveryStatus;
import com.bukang.payout.domain.exception.PayoutMemberNotFoundException;
import com.bukang.payout.domain.exception.PayoutMemberUnavailableException;
import com.bukang.payout.out.MemberAdminPort;
import com.bukang.payout.out.PayoutRecoveryRepository;
import com.bukang.payout.support.RestDocsConfig;

// 통과한 테스트만 REST Docs 스니펫(build/generated-snippets)을 남긴다. 문서는 src/docs/asciidoc/index.adoc에서 묶는다
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Import(RestDocsConfig.class)
@ActiveProfiles("test")
class ApiV1PayoutRecoveryControllerTest {
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
	private PayoutRecoveryRepository payoutRecoveryRepository;

	// 회원 서비스 대신 관리자 여부를 정한다. 정하지 않은 회원은 일반 회원(false)이다
	@MockitoBean
	private MemberAdminPort memberAdminPort;

	@BeforeEach
	void setUp() {
		given(memberAdminPort.isAdmin(ADMIN_ID)).willReturn(true);
	}

	@AfterEach
	void tearDown() {
		payoutRecoveryRepository.deleteAll();
	}

	private PayoutRecovery saveRecovery() {
		return payoutRecoveryRepository.save(new PayoutRecovery(1, 10, PayoutRecoveryReason.RACE_CANCELED, 40_000,
			LocalDateTime.of(2026, 10, 15, 14, 0)));
	}

	private ResultActions complete(long recoveryId) throws Exception {
		return mockMvc.perform(post("/api/v1/payout/recoveries/{recoveryId}/complete", recoveryId)
			.header("X-Member-Id", ADMIN_ID));
	}

	@Test
	@DisplayName("회수를 완료하면 200을 돌려주고, 회수 완료 상태와 확인한 관리자가 남는다")
	void completeRecovery() throws Exception {
		PayoutRecovery recovery = saveRecovery();

		complete(recovery.getId())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(200))
			.andExpect(jsonPath("$.message").value("회수를 완료했습니다."))
			.andExpect(jsonPath("$.data.id").value(recovery.getId()))
			.andExpect(jsonPath("$.data.amount").value(40_000))
			.andExpect(jsonPath("$.data.status").value("COMPLETED"))
			.andExpect(jsonPath("$.data.completedBy").value(ADMIN_ID))
			.andDo(document("payout-recovery-complete",
				requestHeaders(headerWithName("X-Member-Id").description("입금을 확인한 관리자 회원 ID (게이트웨이가 토큰에서 꺼내 넣는다)")),
				pathParameters(parameterWithName("recoveryId").description("완료할 회수 ID")),
				responseFields(
					fieldWithPath("status").description("HTTP 상태 코드"),
					fieldWithPath("message").description("결과 메시지"),
					fieldWithPath("data.id").description("회수 ID"),
					fieldWithPath("data.sellerId").description("돈을 돌려줄 판매자 ID"),
					fieldWithPath("data.raceId").description("대회 ID"),
					fieldWithPath("data.reasonType").description("회수 사유 (RACE_CANCELED: 대회 취소)"),
					fieldWithPath("data.amount").description("회수액 (그 대회에 지급 완료된 정산 내역의 지급액 합)"),
					fieldWithPath("data.status").description("회수 상태 (REQUESTED: 회수 요청, COMPLETED: 회수 완료)"),
					fieldWithPath("data.requestedAt").description("회수 요청 일시 (대회 취소를 처리한 때)"),
					fieldWithPath("data.completedBy").description("입금을 확인한 관리자 회원 ID (요청 중에는 null)")
						.optional().type(JsonFieldType.NUMBER),
					fieldWithPath("data.completedAt").description("회수 완료 일시 (요청 중에는 null)")
						.optional().type(JsonFieldType.STRING)
				)));

		PayoutRecovery reloaded = payoutRecoveryRepository.findById(recovery.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(PayoutRecoveryStatus.COMPLETED);
		assertThat(reloaded.getCompletedBy()).isEqualTo(ADMIN_ID);
	}

	@Test
	@DisplayName("없는 회수를 완료하면 404")
	void notFound() throws Exception {
		complete(9999)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("회수를 찾을 수 없습니다."))
			.andDo(document("payout-recovery-complete-not-found", ERROR_RESPONSE));
	}

	@Test
	@DisplayName("이미 완료한 회수를 다시 완료하면 409")
	void alreadyCompleted() throws Exception {
		PayoutRecovery recovery = saveRecovery();
		complete(recovery.getId()).andExpect(status().isOk());

		complete(recovery.getId())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 완료된 회수입니다."))
			.andDo(document("payout-recovery-complete-conflict", ERROR_RESPONSE));
	}

	@Test
	@DisplayName("일반 회원이 회수를 완료하면 403 PAYOUT_NOT_ADMIN이고, 회수는 그대로다")
	void nonAdminCannotComplete() throws Exception {
		PayoutRecovery recovery = saveRecovery();

		mockMvc.perform(post("/api/v1/payout/recoveries/{recoveryId}/complete", recovery.getId())
				.header("X-Member-Id", MEMBER_ID))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("PAYOUT_NOT_ADMIN"))
			.andExpect(jsonPath("$.message").value("관리자만 이용할 수 있습니다."))
			.andDo(document("payout-recovery-complete-forbidden", ERROR_RESPONSE));
		PayoutRecovery reloaded = payoutRecoveryRepository.findById(recovery.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(PayoutRecoveryStatus.REQUESTED);
		assertThat(reloaded.getCompletedBy()).isNull();
	}

	@Test
	@DisplayName("회원 ID가 0 이하면 400이고, 회수는 그대로다")
	void invalidMemberId() throws Exception {
		PayoutRecovery recovery = saveRecovery();

		mockMvc.perform(post("/api/v1/payout/recoveries/{recoveryId}/complete", recovery.getId())
				.header("X-Member-Id", 0))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value("회원 ID가 올바르지 않습니다."));
		assertThat(payoutRecoveryRepository.findById(recovery.getId()).orElseThrow().getStatus())
			.isEqualTo(PayoutRecoveryStatus.REQUESTED);
	}

	@Test
	@DisplayName("일반 회원이 없는 회수를 완료해도 404가 아니라 403이다 (관리자 확인이 먼저다)")
	void nonAdminGetsForbiddenBeforeNotFound() throws Exception {
		mockMvc.perform(post("/api/v1/payout/recoveries/{recoveryId}/complete", 9999)
				.header("X-Member-Id", MEMBER_ID))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("PAYOUT_NOT_ADMIN"));
	}

	@Test
	@DisplayName("헤더의 회원이 없거나 탈퇴했으면 404 PAYOUT_MEMBER_NOT_FOUND이고, 회수는 그대로다")
	void unknownMemberIsNotFound() throws Exception {
		PayoutRecovery recovery = saveRecovery();
		given(memberAdminPort.isAdmin(MEMBER_ID)).willThrow(new PayoutMemberNotFoundException());

		mockMvc.perform(post("/api/v1/payout/recoveries/{recoveryId}/complete", recovery.getId())
				.header("X-Member-Id", MEMBER_ID))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PAYOUT_MEMBER_NOT_FOUND"));
		assertThat(payoutRecoveryRepository.findById(recovery.getId()).orElseThrow().getStatus())
			.isEqualTo(PayoutRecoveryStatus.REQUESTED);
	}

	@Test
	@DisplayName("회원 서비스가 응답하지 않으면 503 PAYOUT_MEMBER_UNAVAILABLE이고, 관리자여도 회수는 그대로다")
	void memberServiceUnavailable() throws Exception {
		PayoutRecovery recovery = saveRecovery();
		given(memberAdminPort.isAdmin(ADMIN_ID))
			.willThrow(new PayoutMemberUnavailableException(new IllegalStateException("타임아웃")));

		complete(recovery.getId())
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("PAYOUT_MEMBER_UNAVAILABLE"));
		PayoutRecovery reloaded = payoutRecoveryRepository.findById(recovery.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(PayoutRecoveryStatus.REQUESTED);
		assertThat(reloaded.getCompletedBy()).isNull();
	}
}
