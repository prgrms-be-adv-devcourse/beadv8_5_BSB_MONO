package com.bukang.payout.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

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
import com.bukang.payout.domain.PayoutItem;
import com.bukang.payout.domain.PayoutRecordType;
import com.bukang.payout.domain.PayoutRecovery;
import com.bukang.payout.out.MemberAdminPort;
import com.bukang.payout.out.PayoutClaimRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;
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
		payoutItemRepository.deleteAll();
		payoutRecoveryRepository.deleteAll();
		payoutClaimRepository.deleteAll();
	}

	// 청구 금액과 같은 갚을 돈(회수 요청 105,500)을 함께 남긴다. 확인할 때 둘이 다르면 거부한다
	private PayoutClaim saveClaim() {
		PayoutRecovery recovery = payoutRecoveryRepository.save(
			PayoutRecovery.scheduleChanged(1, 7, 108, 105_500, LocalDateTime.of(2026, 11, 26, 10, 0)));
		payoutItemRepository.save(
			PayoutItem.ofRecovery(recovery, PayoutRecordType.갚을_돈_추가, 108L, 7L, 105_500));
		PayoutClaim claim = payoutClaimRepository.save(new PayoutClaim(1, 7, 105_500, LocalDate.of(2026, 12, 14)));
		claim.assignClaimCode(LocalDate.of(2026, 12, 15));
		return payoutClaimRepository.save(claim);
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
			.andExpect(jsonPath("$.data.claimCode").value(claim.getClaimCode()))
			.andExpect(jsonPath("$.data.amount").value(105_500))
			.andExpect(jsonPath("$.data.dueDate").value("2027-01-13"))
			.andExpect(jsonPath("$.data.status").value("입금_확인"))
			.andExpect(jsonPath("$.data.confirmedBy").value(ADMIN_ID))
			.andDo(document("payout-claim-confirm",
				requestHeaders(headerWithName("X-Member-Id").description("입금을 확인한 관리자 회원 ID (게이트웨이가 토큰에서 꺼내 넣는다)")),
				pathParameters(parameterWithName("claimId").description("입금을 확인할 청구 ID")),
				responseFields(
					fieldWithPath("status").description("HTTP 상태 코드"),
					fieldWithPath("message").description("결과 메시지"),
					fieldWithPath("data.id").description("청구 ID"),
					fieldWithPath("data.claimCode").description("청구 ID 표기 (RC-만든 날-16진수 4자리)"),
					fieldWithPath("data.sellerId").description("돈을 돌려줄 판매자 ID"),
					fieldWithPath("data.raceId").description("대회 ID"),
					fieldWithPath("data.amount").description("청구 금액 (최종 정산 상계 뒤 남은 갚을 돈)"),
					fieldWithPath("data.startDate").description("상계 실패일 (최종 정산을 만든 날, 대회 취소는 취소일)"),
					fieldWithPath("data.dueDate").description("판매자 입금 기한 (상계 실패일 + 30일)"),
					fieldWithPath("data.status").description("청구 상태 (청구_중: 청구 중, 입금_확인: 입금 확인)"),
					fieldWithPath("data.confirmedBy").description("입금을 확인한 관리자 회원 ID (확인 전에는 null)")
						.optional().type(JsonFieldType.NUMBER),
					fieldWithPath("data.confirmedAt").description("입금 확인 일시 (확인 전에는 null)")
						.optional().type(JsonFieldType.STRING),
					fieldWithPath("data.virtualBankName").description("판매자가 입금할 가상계좌 은행 이름 (발급 전에는 null)")
						.optional().type(JsonFieldType.STRING),
					fieldWithPath("data.virtualAccountNumber").description("판매자가 입금할 가상계좌 번호 (발급 전에는 null)")
						.optional().type(JsonFieldType.STRING)
				)));

		assertThat(payoutClaimRepository.findById(claim.getId()).orElseThrow().getStatus())
			.isEqualTo(PayoutClaimStatus.입금_확인);
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
	@DisplayName("TC-REV2-2 청구 뒤 갚을 돈이 185,500원으로 늘었으면 409와 \"예상치 못한 일\" 메시지를 돌려주고, 청구와 원장은 그대로다")
	void receivableChangedAfterClaim() throws Exception {
		PayoutClaim claim = saveClaim();
		PayoutRecovery more = payoutRecoveryRepository.save(
			PayoutRecovery.scheduleChanged(1, 7, 109, 80_000, LocalDateTime.of(2026, 12, 15, 10, 0)));
		payoutItemRepository.save(PayoutItem.ofRecovery(more, PayoutRecordType.갚을_돈_추가, 109L, 7L, 80_000));
		long lines = payoutItemRepository.count();

		confirm(claim.getId(), ADMIN_ID)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("예상치 못한 일이 발생해 회수를 확인할 수 없습니다. 관리자에게 문의해 주세요."))
			.andDo(document("payout-claim-confirm-receivable-changed", ERROR_RESPONSE));
		assertThat(payoutClaimRepository.findById(claim.getId()).orElseThrow().getStatus())
			.isEqualTo(PayoutClaimStatus.청구_중);
		assertThat(payoutItemRepository.count()).isEqualTo(lines);
	}

	@Test
	@DisplayName("TC-CONFIRM-4-2 관리자가 아닌 회원이 청구 입금을 확인하면 403이고, 청구와 원장은 그대로다")
	void nonAdminCannotConfirm() throws Exception {
		PayoutClaim claim = saveClaim();
		long lines = payoutItemRepository.count();

		confirm(claim.getId(), MEMBER_ID)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("PAYOUT_NOT_ADMIN"))
			.andDo(document("payout-claim-confirm-forbidden", ERROR_RESPONSE));
		assertThat(payoutClaimRepository.findById(claim.getId()).orElseThrow().getStatus())
			.isEqualTo(PayoutClaimStatus.청구_중);
		assertThat(payoutItemRepository.count()).isEqualTo(lines);
	}

	// 관리자 회수 관리 목록용 청구. 입금 확인 시각을 주면 확인한 청구로 남긴다
	private PayoutClaim saveClaim(long sellerId, long raceId, long amount, LocalDateTime confirmedAt) {
		PayoutClaim claim = payoutClaimRepository.save(
			new PayoutClaim(sellerId, raceId, amount, LocalDate.of(2026, 12, 14)));
		claim.assignClaimCode(LocalDate.of(2026, 12, 15));
		if (confirmedAt != null) {
			claim.confirm(ADMIN_ID, confirmedAt);
		}
		return payoutClaimRepository.save(claim);
	}

	private ResultActions adminClaims(long memberId, String... params) throws Exception {
		var request = get("/api/v1/payout/admin/claims").header("X-Member-Id", memberId);
		for (int i = 0; i < params.length; i += 2) {
			request.queryParam(params[i], params[i + 1]);
		}
		return mockMvc.perform(request);
	}

	@Test
	@DisplayName("관리자 회수 관리: 최근 청구부터 목록을 주고, 카드 숫자는 청구 중 건수 · 금액 합계 · 이번 달 입금 확인 건수다")
	void adminClaimsList() throws Exception {
		LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
		saveClaim(1, 7, 105_500, null);
		saveClaim(2, 7, 30_000, null);
		saveClaim(3, 7, 50_000, now);
		PayoutClaim lastMonth = saveClaim(4, 7, 70_000, now.minusMonths(1));
		lastMonth.assignVirtualAccount("국민은행", "12345678901234");
		payoutClaimRepository.save(lastMonth);

		adminClaims(ADMIN_ID, "page", "0", "size", "20")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("청구 목록을 조회했습니다."))
			.andExpect(jsonPath("$.data.summary.claimingCount").value(2))
			.andExpect(jsonPath("$.data.summary.claimingAmount").value(135_500))
			.andExpect(jsonPath("$.data.summary.confirmedThisMonthCount").value(1))
			.andExpect(jsonPath("$.data.claims.totalElements").value(4))
			.andExpect(jsonPath("$.data.claims.content[0].id").value(lastMonth.getId()))
			.andExpect(jsonPath("$.data.claims.content[0].virtualBankName").value("국민은행"))
			.andExpect(jsonPath("$.data.claims.content[0].virtualAccountNumber").value("12345678901234"))
			// 캐시가 가상계좌를 아직 발급하지 않은 청구는 null
			.andExpect(jsonPath("$.data.claims.content[1].virtualBankName").doesNotExist())
			.andDo(document("payout-admin-claims",
				requestHeaders(headerWithName("X-Member-Id").description("관리자 회원 ID (게이트웨이가 토큰에서 꺼내 넣는다)")),
				queryParameters(
					parameterWithName("status").description("청구 상태 (청구_중, 입금_확인). 없으면 전체").optional(),
					parameterWithName("code").description("청구 ID 표기(RC-)로 한 건 찾기. 주면 status는 보지 않는다")
						.optional(),
					parameterWithName("page").description("페이지 번호 (0부터, 기본 0)").optional(),
					parameterWithName("size").description("페이지 크기 (1 ~ 100, 기본 50)").optional()),
				responseFields(
					fieldWithPath("status").description("HTTP 상태 코드"),
					fieldWithPath("message").description("결과 메시지"),
					fieldWithPath("data.summary.claimingCount").description("청구 중 건수 (전체 기준)"),
					fieldWithPath("data.summary.claimingAmount").description("회수할 금액 합계 = 청구 중 청구 금액 합"),
					fieldWithPath("data.summary.confirmedThisMonthCount")
						.description("이번 달(한국 시간) 입금을 확인한 청구 수"),
					fieldWithPath("data.claims.content[].id").description("청구 ID"),
					fieldWithPath("data.claims.content[].claimCode").description("청구 ID 표기 (RC-만든 날-16진수 4자리)"),
					fieldWithPath("data.claims.content[].sellerId")
						.description("판매자 ID. 판매자 이름은 프론트가 회원 정보에서 따로 받는다"),
					fieldWithPath("data.claims.content[].raceId").description("대회 ID"),
					fieldWithPath("data.claims.content[].amount").description("청구 금액"),
					fieldWithPath("data.claims.content[].startDate").description("상계 실패일"),
					fieldWithPath("data.claims.content[].dueDate").description("판매자 입금 기한 (상계 실패일 + 30일)"),
					fieldWithPath("data.claims.content[].status").description("청구 상태 (청구_중, 입금_확인)"),
					fieldWithPath("data.claims.content[].confirmedBy").description("입금을 확인한 관리자 회원 ID (확인 전 null)")
						.optional().type(JsonFieldType.NUMBER),
					fieldWithPath("data.claims.content[].confirmedAt").description("입금 확인 일시 (확인 전 null)")
						.optional().type(JsonFieldType.STRING),
					fieldWithPath("data.claims.content[].virtualBankName")
						.description("판매자가 입금할 가상계좌 은행 이름 (발급 전 null)").optional().type(JsonFieldType.STRING),
					fieldWithPath("data.claims.content[].virtualAccountNumber")
						.description("판매자가 입금할 가상계좌 번호 (발급 전 null)").optional().type(JsonFieldType.STRING),
					fieldWithPath("data.claims.page").description("페이지 번호 (0부터)"),
					fieldWithPath("data.claims.size").description("페이지 크기"),
					fieldWithPath("data.claims.totalElements").description("전체 청구 수"),
					fieldWithPath("data.claims.totalPages").description("전체 페이지 수")
				)));
	}

	@Test
	@DisplayName("관리자 회수 관리: status로 청구 중만 거른다. 카드 숫자는 거르지 않은 전체 기준이다")
	void adminClaimsByStatus() throws Exception {
		saveClaim(1, 7, 105_500, null);
		saveClaim(3, 7, 50_000, LocalDateTime.now(ZoneId.of("Asia/Seoul")));

		adminClaims(ADMIN_ID, "status", "청구_중")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.claims.totalElements").value(1))
			.andExpect(jsonPath("$.data.claims.content[0].status").value("청구_중"))
			.andExpect(jsonPath("$.data.summary.confirmedThisMonthCount").value(1));
	}

	@Test
	@DisplayName("관리자 회수 관리: RC- 청구 ID로 한 건을 찾는다. 앞뒤 공백 · 소문자도 찾는다")
	void adminClaimsByCode() throws Exception {
		PayoutClaim target = saveClaim(1, 7, 105_500, null);
		saveClaim(2, 7, 30_000, null);

		adminClaims(ADMIN_ID, "code", " " + target.getClaimCode().toLowerCase() + " ")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.claims.totalElements").value(1))
			.andExpect(jsonPath("$.data.claims.content[0].claimCode").value(target.getClaimCode()))
			.andDo(document("payout-admin-claims-by-code"));
	}

	@Test
	@DisplayName("관리자 회수 관리: 청구가 없으면 빈 목록과 0인 카드 숫자를 준다 (N6)")
	void adminClaimsEmpty() throws Exception {
		adminClaims(ADMIN_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.claims.content").isEmpty())
			.andExpect(jsonPath("$.data.claims.totalElements").value(0))
			.andExpect(jsonPath("$.data.summary.claimingCount").value(0))
			.andExpect(jsonPath("$.data.summary.claimingAmount").value(0))
			.andExpect(jsonPath("$.data.summary.confirmedThisMonthCount").value(0))
			.andDo(document("payout-admin-claims-empty"));
	}

	@Test
	@DisplayName("관리자 회수 관리: 없는 RC- 청구 ID면 빈 목록이다")
	void adminClaimsUnknownCode() throws Exception {
		saveClaim(1, 7, 105_500, null);

		adminClaims(ADMIN_ID, "code", "RC-20990101-FFFF")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.claims.content").isEmpty());
	}

	@Test
	@DisplayName("관리자 회수 관리: RC- 청구 ID로 찾을 때 page를 1로 줘도 첫 페이지의 1건만 준다")
	void adminClaimsByCodeIgnoresPage() throws Exception {
		PayoutClaim target = saveClaim(1, 7, 105_500, null);

		adminClaims(ADMIN_ID, "code", target.getClaimCode(), "page", "1", "size", "50")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.claims.content[0].claimCode").value(target.getClaimCode()))
			.andExpect(jsonPath("$.data.claims.page").value(0))
			.andExpect(jsonPath("$.data.claims.totalElements").value(1))
			.andExpect(jsonPath("$.data.claims.totalPages").value(1));
	}

	@Test
	@DisplayName("관리자 회수 관리: 관리자가 아니면 403")
	void adminClaimsForbidden() throws Exception {
		adminClaims(MEMBER_ID)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("PAYOUT_NOT_ADMIN"))
			.andDo(document("payout-admin-claims-forbidden", ERROR_RESPONSE));
	}

	@Test
	@DisplayName("관리자 회수 관리: 페이지 크기가 100을 넘으면 400")
	void adminClaimsInvalidSize() throws Exception {
		adminClaims(ADMIN_ID, "size", "101")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("페이지 크기는 1 이상 100 이하여야 합니다."));
	}
}
