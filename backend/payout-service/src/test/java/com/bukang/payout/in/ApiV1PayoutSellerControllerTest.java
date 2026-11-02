package com.bukang.payout.in;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

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
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutHold;
import com.bukang.payout.domain.PayoutHoldReason;
import com.bukang.payout.domain.exception.PayoutSellerLookupUnavailableException;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.SellerLookupPort;
import com.bukang.payout.out.SellerLookupPort.SellerOfMember;
import com.bukang.payout.support.RestDocsConfig;

import jakarta.persistence.EntityManager;

// 통과한 테스트만 REST Docs 스니펫(build/generated-snippets)을 남긴다. 문서는 src/docs/asciidoc/index.adoc에서 묶는다
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Import(RestDocsConfig.class)
@ActiveProfiles("test")
class ApiV1PayoutSellerControllerTest {
	private static final long SELLER_ID = 1;
	private static final long OTHER_SELLER_ID = 2;
	// 정산 내역이 하나도 없는 판매자
	private static final long NEW_SELLER_ID = 3;
	// 판매자 1의 회원 ID. 판매자 ID와 다른 번호로 두어, 회원 ID를 그대로 판매자 ID로 쓰면 조회가 비게 한다
	// 회원 2는 판매자 2와 같은 번호지만 판매자가 아니다 (회원 ID로 찾으면 남의 정산이 보이는 경우)
	private static final long MEMBER_ID = 7;
	private static final long NOT_SELLER_MEMBER_ID = 2;
	private static final BigDecimal FEE_RATE = new BigDecimal("0.0550");
	private static final BusinessDayCalendar CALENDAR = new BusinessDayCalendar();
	private static final String URL = "/api/v1/payout/seller";

	private static final HeaderDescriptor MEMBER_ID_HEADER =
		headerWithName("X-Member-Id").description("판매자의 회원 ID (게이트웨이가 Access Token에서 꺼내 넣는다). 판매자 ID와 다른 값이다");

	private static final Snippet ERROR_RESPONSE = responseFields(
		fieldWithPath("status").description("HTTP 상태 코드"),
		fieldWithPath("code").description("오류 코드 (예: 로그인 필요는 LOGIN_REQUIRED). 코드가 없는 오류는 이 필드가 없다")
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

	@Autowired
	private TransactionTemplate transactionTemplate;

	// 회원 서비스는 띄우지 않는다. 회원 ID → 판매자 ID 변환 결과만 정해 둔다 (어댑터는 MemberSellerLookupAdapterTest)
	@MockitoBean
	private SellerLookupPort sellerLookupPort;

	@Autowired
	private EntityManager entityManager;

	// 판매자 1: 2026-09 정산 예정(9/25 대회 → 9/28 지급, 참가권 2장), 2026-08 지급보류(9/20 보류), 2025-08 지급 완료
	// 판매자 2: 2026-09 정산 예정 (판매자 1의 조회에 나오면 안 된다)
	@BeforeEach
	void setUp() {
		given(sellerLookupPort.findSellerOf(MEMBER_ID))
			.willReturn(Optional.of(new SellerOfMember(SELLER_ID, SellerStatus.APPROVED)));

		transactionTemplate.executeWithoutResult(status -> {
			Payout september = savePayout(SELLER_ID, 10, LocalDate.of(2026, 9, 26));
			september.addItem(100, 50_000L, FEE_RATE, LocalDate.of(2026, 9, 26));
			september.addItem(101, 50_000L, FEE_RATE, LocalDate.of(2026, 9, 26));

			Payout august = savePayout(SELLER_ID, 11, LocalDate.of(2026, 8, 16));
			august.addItem(200, 30_000L, FEE_RATE, LocalDate.of(2026, 8, 16));
			PayoutHold hold = august.hold(PayoutHoldReason.ACCOUNT_NOT_REGISTERED, "정산 계좌 등록 요청 중",
				PayoutHold.SYSTEM_MEMBER_ID, LocalDateTime.of(2026, 9, 20, 10, 0));
			entityManager.persist(hold);
			entityManager.flush();
			hold.assignHoldCode();

			Payout lastYear = savePayout(SELLER_ID, 12, LocalDate.of(2025, 8, 10));
			lastYear.pay(LocalDateTime.of(2025, 9, 22, 4, 0));

			Payout other = savePayout(OTHER_SELLER_ID, 10, LocalDate.of(2026, 9, 26));
			other.addItem(300, 50_000L, FEE_RATE, LocalDate.of(2026, 9, 26));
		});
	}

	@AfterEach
	void tearDown() {
		payoutHoldRepository.deleteAll();
		payoutRepository.deleteAll();
	}

	// 최종 정산 대상일(targetDate)은 대회 다음 날이다
	private Payout savePayout(long sellerId, long raceId, LocalDate targetDate) {
		Payout payout = Payout.finalStage(sellerId, raceId, "대회 %d".formatted(raceId), targetDate.minusDays(1),
			targetDate.atTime(3, 0),
			CALENDAR);
		entityManager.persist(payout);
		entityManager.flush();
		payout.assignPayoutCode();
		return payout;
	}

	private ResultActions request(String path, String... params) throws Exception {
		var builder = get(URL + path).header("X-Member-Id", MEMBER_ID);
		for (int i = 0; i < params.length; i += 2) {
			builder.param(params[i], params[i + 1]);
		}
		return mockMvc.perform(builder);
	}

	@Test
	@DisplayName("요약: 가장 이른 지급 예정일과 그날 나갈 지급액, 보류 건수를 돌려준다 (보류된 정산 내역은 예정액에서 빠진다)")
	void summary() throws Exception {
		request("/summary")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("정산 요약을 조회했습니다."))
			.andExpect(jsonPath("$.data.nextPayDate").value("2026-09-27"))
			.andExpect(jsonPath("$.data.scheduledPayoutAmount").value(94_500))
			.andExpect(jsonPath("$.data.heldCount").value(1))
			.andDo(document("payout-seller-summary",
				requestHeaders(MEMBER_ID_HEADER),
				responseFields(
					fieldWithPath("status").description("HTTP 상태 코드"),
					fieldWithPath("message").description("결과 메시지"),
					fieldWithPath("data.nextPayDate").type(JsonFieldType.STRING).optional().description(
						"다음 정산일. 지급 예정 정산 내역 중 가장 이른 지급 예정일 (1차를 기다리는 최종은 뺀다). "
							+ "지급 예정 정산 내역이 없으면 null"),
					fieldWithPath("data.scheduledPayoutAmount").description(
						"정산 예정액. 다음 정산일에 나갈 지급액 합계 (보류 · 1차를 기다리는 최종 제외). 다음 정산일이 null이면 0"),
					fieldWithPath("data.heldCount").description("지급보류 중인 정산 내역 수")
				)));
	}

	@Test
	@DisplayName("요약: 1차가 아직 지급되지 않아 기다리는 최종은 다음 정산일 · 정산 예정액에서 뺀다 (지급 배치가 건너뛰므로, PRO-188)")
	void summarySkipsFinalWaitingForFirst() throws Exception {
		given(sellerLookupPort.findSellerOf(MEMBER_ID))
			.willReturn(Optional.of(new SellerOfMember(NEW_SELLER_ID, SellerStatus.APPROVED)));
		transactionTemplate.executeWithoutResult(status -> {
			// 대회 20: 1차(마감 9/10 → 9/12)는 보류, 최종(대회 9/15 → 9/17)은 정산 예정이지만 1차를 기다린다
			Payout first = Payout.first(NEW_SELLER_ID, 20, "대회 20", LocalDate.of(2026, 9, 10),
				LocalDateTime.of(2026, 9, 11, 3, 0), CALENDAR);
			entityManager.persist(first);
			entityManager.flush();
			PayoutHold hold = first.hold(PayoutHoldReason.ACCOUNT_NOT_REGISTERED, "정산 계좌 등록 요청 중",
				PayoutHold.SYSTEM_MEMBER_ID, LocalDateTime.of(2026, 9, 11, 3, 0));
			entityManager.persist(hold);
			entityManager.flush();
			hold.assignHoldCode();
			Payout waiting = savePayout(NEW_SELLER_ID, 20, LocalDate.of(2026, 9, 16));
			waiting.addItem(400, 50_000L, FEE_RATE, LocalDate.of(2026, 9, 16));

			// 대회 21: 1차 없이 만든 최종(대회 9/20 → 9/22)은 기다리지 않는다
			Payout ready = savePayout(NEW_SELLER_ID, 21, LocalDate.of(2026, 9, 21));
			ready.addItem(500, 30_000L, FEE_RATE, LocalDate.of(2026, 9, 21));
		});

		request("/summary")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.nextPayDate").value("2026-09-22"))
			.andExpect(jsonPath("$.data.scheduledPayoutAmount").value(28_350))
			.andExpect(jsonPath("$.data.heldCount").value(1));
	}

	@Test
	@DisplayName("요약: 지급 예정 정산 내역이 없으면 다음 정산일은 null, 정산 예정액은 0이다 (정기 지급일이 없어 대신 채울 날이 없다)")
	void summaryWithoutScheduledPayout() throws Exception {
		given(sellerLookupPort.findSellerOf(MEMBER_ID))
			.willReturn(Optional.of(new SellerOfMember(NEW_SELLER_ID, SellerStatus.APPROVED)));

		request("/summary")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.nextPayDate").value(nullValue()))
			.andExpect(jsonPath("$.data.scheduledPayoutAmount").value(0))
			.andExpect(jsonPath("$.data.heldCount").value(0))
			.andDo(document("payout-seller-summary-empty",
				requestHeaders(MEMBER_ID_HEADER),
				responseFields(
					fieldWithPath("status").description("HTTP 상태 코드"),
					fieldWithPath("message").description("결과 메시지"),
					fieldWithPath("data.nextPayDate").type(JsonFieldType.STRING).optional()
						.description("다음 정산일. 지급 예정 정산 내역이 없어 null"),
					fieldWithPath("data.scheduledPayoutAmount").description("정산 예정액. 다음 정산일이 없어 0"),
					fieldWithPath("data.heldCount").description("지급보류 중인 정산 내역 수")
				)));
	}

	@Test
	@DisplayName("정산 내역: 정산 월이 기간 안인 내 정산 내역만 최근 달부터 돌려준다")
	void payouts() throws Exception {
		request("/payouts", "fromMonth", "2025-11", "toMonth", "2026-10")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("정산 내역을 조회했습니다."))
			.andExpect(jsonPath("$.data", hasSize(2)))
			.andExpect(jsonPath("$.data[*].payoutMonth", contains("2026-09", "2026-08")))
			.andExpect(jsonPath("$.data[0].salesAmount").value(100_000))
			.andExpect(jsonPath("$.data[0].feeAmount").value(5_500))
			.andExpect(jsonPath("$.data[0].payoutAmount").value(94_500))
			.andExpect(jsonPath("$.data[0].scheduledPayDate").value("2026-09-27"))
			.andExpect(jsonPath("$.data[0].raceName").value("대회 10"))
			.andExpect(jsonPath("$.data[1].status").value("HELD"))
			.andDo(document("payout-seller-payouts",
				requestHeaders(MEMBER_ID_HEADER),
				queryParameters(
					parameterWithName("fromMonth").description("조회 시작 정산 월 (yyyy-MM)"),
					parameterWithName("toMonth").description("조회 끝 정산 월 (yyyy-MM, 포함). 시작 달을 포함해 최대 12개월")
				),
				responseFields(
					fieldWithPath("status").description("HTTP 상태 코드"),
					fieldWithPath("message").description("결과 메시지"),
					fieldWithPath("data[].id").description("정산 내역 ID"),
					fieldWithPath("data[].payoutCode").description("정산 ID 표기 (ST-정산 내역을 만든 날-16진수 4자리)"),
					fieldWithPath("data[].raceId").description("대회 ID"),
					fieldWithPath("data[].raceName").description("대회 이름 (정산 내역을 만들 때 저장한 값)"),
					fieldWithPath("data[].payoutStage").description("정산 단계 (FIRST: 1차 선금 40%, FINAL: 최종)"),
					fieldWithPath("data[].payoutMonth").description("정산 월 (1차는 환불 마감일, 최종은 대회일이 속한 달, yyyy-MM)"),
					fieldWithPath("data[].salesAmount").description("판매액"),
					fieldWithPath("data[].refundAmount").description("환불액 (1차 생성 뒤 환불. 지금은 항상 0)"),
					fieldWithPath("data[].feeAmount").description("플랫폼 수수료 (최종만. 참가권마다 5.5%, 원 미만 버림의 합)"),
					fieldWithPath("data[].advanceDeductionAmount").description("선지급 차감 (최종만. 1차 지급액 − 회수액)"),
					fieldWithPath("data[].payoutAmount").description(
						"지급액 (1차: 참가권마다 가격 × 40% 버림의 합, 최종: 판매액 − 환불액 − 수수료 − 선지급 차감)"),
					fieldWithPath("data[].status").description("상태 (CALCULATED: 정산 예정, HELD: 지급보류, PAID: 지급 완료, "
						+ "CANCELED: 대회 취소로 닫힘)"),
					fieldWithPath("data[].settledAt").description("정산 내역을 만든 일시"),
					fieldWithPath("data[].scheduledPayDate").description(
						"지급 예정일. 1차는 환불 마감일 + 2일, 최종은 대회일 + 2일 (보류를 풀면 원래 지급 예정일과 해제일 + 1일 중 늦은 날로 다시 정한다)"),
					fieldWithPath("data[].paidAt").description("지급 완료 일시 (지급 전에는 null)")
						.optional().type(JsonFieldType.STRING)
				)));
	}

	@Test
	@DisplayName("건별 정산: 정산 대상일이 기간 안인 내 정산 항목만 페이지로 돌려준다")
	void items() throws Exception {
		request("/payout-items", "from", "2026-09-01", "to", "2026-09-30", "page", "0", "size", "50")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("건별 정산을 조회했습니다."))
			.andExpect(jsonPath("$.data.content", hasSize(2)))
			.andExpect(jsonPath("$.data.content[*].orderItemId", contains(101, 100)))
			.andExpect(jsonPath("$.data.content[0].feeAmount").value(2_750))
			.andExpect(jsonPath("$.data.content[0].payoutStatus").value("CALCULATED"))
			.andExpect(jsonPath("$.data.content[0].raceName").value("대회 10"))
			.andExpect(jsonPath("$.data.totalElements").value(2))
			.andDo(document("payout-seller-items",
				requestHeaders(MEMBER_ID_HEADER),
				queryParameters(
					parameterWithName("from").description("조회 시작 정산 대상일 (yyyy-MM-dd)"),
					parameterWithName("to").description("조회 끝 정산 대상일 (yyyy-MM-dd, 포함). 시작 날 + 1개월 전까지"),
					parameterWithName("page").description("페이지 번호 (0부터, 기본 0)").optional(),
					parameterWithName("size").description("페이지 크기 (1 ~ 100, 기본 50)").optional()
				),
				responseFields(
					fieldWithPath("status").description("HTTP 상태 코드"),
					fieldWithPath("message").description("결과 메시지"),
					fieldWithPath("data.content[].id").description("정산 항목 ID"),
					fieldWithPath("data.content[].orderItemId").description("주문 상품 ID (참가권 1장)"),
					fieldWithPath("data.content[].recordType").description("종류 (SALE: 판매. 세미는 판매만 생긴다)"),
					fieldWithPath("data.content[].amount").description("금액"),
					fieldWithPath("data.content[].feeRate").description("기록 시점의 수수료율"),
					fieldWithPath("data.content[].feeAmount").description("수수료 (금액 × 수수료율, 원 미만 버림)"),
					fieldWithPath("data.content[].targetDate").description("정산 대상일 (대회 다음 날)"),
					fieldWithPath("data.content[].payoutId").description("묶인 정산 내역 ID"),
					fieldWithPath("data.content[].payoutCode").description("묶인 정산 내역의 정산 ID 표기"),
					fieldWithPath("data.content[].raceId").description("대회 ID"),
					fieldWithPath("data.content[].raceName").description("대회 이름"),
					fieldWithPath("data.content[].payoutStatus").description("묶인 정산 내역의 상태"),
					fieldWithPath("data.content[].scheduledPayDate").description("묶인 정산 내역의 지급 예정일"),
					fieldWithPath("data.page").description("현재 페이지 번호 (0부터)"),
					fieldWithPath("data.size").description("페이지 크기"),
					fieldWithPath("data.totalElements").description("기간 안 전체 정산 항목 수"),
					fieldWithPath("data.totalPages").description("전체 페이지 수")
				)));
	}

	@Test
	@DisplayName("건별 정산: 크기 1로 두 번째 페이지를 달라고 하면 두 번째로 최근인 항목 하나와 전체 개수를 돌려준다")
	void itemsSecondPage() throws Exception {
		request("/payout-items", "from", "2026-09-01", "to", "2026-09-30", "page", "1", "size", "1")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.content[*].orderItemId", contains(100)))
			.andExpect(jsonPath("$.data.page").value(1))
			.andExpect(jsonPath("$.data.size").value(1))
			.andExpect(jsonPath("$.data.totalElements").value(2))
			.andExpect(jsonPath("$.data.totalPages").value(2));
	}

	@Test
	@DisplayName("건별 정산: 페이지를 안 주면 0페이지 · 크기 50이다")
	void itemsDefaultPage() throws Exception {
		request("/payout-items", "from", "2026-09-01", "to", "2026-09-30")
			.andExpect(jsonPath("$.data.page").value(0))
			.andExpect(jsonPath("$.data.size").value(50));
	}

	@Test
	@DisplayName("건별 정산: 페이지 번호가 음수거나 크기가 1 ~ 100 밖이면 400")
	void itemsInvalidPage() throws Exception {
		request("/payout-items", "from", "2026-09-01", "to", "2026-09-30", "page", "-1")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("페이지 번호는 0 이상이어야 합니다."))
			.andDo(document("payout-seller-items-invalid-page", ERROR_RESPONSE));
		request("/payout-items", "from", "2026-09-01", "to", "2026-09-30", "size", "101")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("페이지 크기는 1 이상 100 이하여야 합니다."));
		request("/payout-items", "from", "2026-09-01", "to", "2026-09-30", "size", "0")
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("지급보류 내역: 보류한 날이 기간 안인 내 보류를 정산 ID · 지급액과 함께 돌려준다")
	void holds() throws Exception {
		request("/holds", "from", "2026-09-15", "to", "2026-10-14")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("지급보류 내역을 조회했습니다."))
			.andExpect(jsonPath("$.data", hasSize(1)))
			.andExpect(jsonPath("$.data[0].reasonType").value("ACCOUNT_NOT_REGISTERED"))
			.andExpect(jsonPath("$.data[0].status").value("HELD"))
			.andExpect(jsonPath("$.data[0].payoutAmount").value(28_350))
			.andExpect(jsonPath("$.data[0].raceName").value("대회 11"))
			.andDo(document("payout-seller-holds",
				requestHeaders(MEMBER_ID_HEADER),
				queryParameters(
					parameterWithName("from").description("조회 시작 보류일 (yyyy-MM-dd)"),
					parameterWithName("to").description("조회 끝 보류일 (yyyy-MM-dd, 포함). 시작 날 + 1개월 전까지")
				),
				responseFields(
					fieldWithPath("status").description("HTTP 상태 코드"),
					fieldWithPath("message").description("결과 메시지"),
					fieldWithPath("data[].id").description("지급보류 ID"),
					fieldWithPath("data[].holdCode").description("지급보류 ID 표기 (HD-보류한 날-16진수 4자리)"),
					fieldWithPath("data[].payoutId").description("보류된 정산 내역 ID"),
					fieldWithPath("data[].payoutCode").description("보류된 정산 내역의 정산 ID 표기"),
					fieldWithPath("data[].raceId").description("대회 ID"),
					fieldWithPath("data[].raceName").description("대회 이름"),
					fieldWithPath("data[].payoutAmount").description("보류된 정산 내역의 지급액"),
					fieldWithPath("data[].reasonType").description(
						"보류 사유 (ACCOUNT_NOT_REGISTERED: 정산 계좌 미등록, RACE_CANCELED: 대회 취소 처리)"),
					fieldWithPath("data[].reason").description("사유 상세").optional().type(JsonFieldType.STRING),
					fieldWithPath("data[].status").description("보류 상태 (HELD: 지급보류, RELEASED: 해제)"),
					fieldWithPath("data[].heldAt").description("보류 일시"),
					fieldWithPath("data[].releasedAt").description("해제 일시 (보류 중에는 null)")
						.optional().type(JsonFieldType.STRING)
				)));
	}

	@Test
	@DisplayName("보류 기간의 끝 날은 그날 23시 59분까지 포함하고, 범위 밖 보류는 나오지 않는다")
	void holdsBoundary() throws Exception {
		request("/holds", "from", "2026-09-01", "to", "2026-09-20")
			.andExpect(jsonPath("$.data", hasSize(1)));
		request("/holds", "from", "2026-09-21", "to", "2026-10-20")
			.andExpect(jsonPath("$.data", hasSize(0)));
	}

	@Test
	@DisplayName("정산 내역을 12개월 넘게 조회하면 400")
	void payoutsTooLong() throws Exception {
		request("/payouts", "fromMonth", "2025-11", "toMonth", "2026-11")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("정산 내역은 최대 12개월까지 조회할 수 있습니다."))
			.andDo(document("payout-seller-payouts-too-long", ERROR_RESPONSE));
	}

	@Test
	@DisplayName("건별 정산 · 지급보류를 1개월 넘게 조회하면 400")
	void detailTooLong() throws Exception {
		request("/payout-items", "from", "2026-09-15", "to", "2026-10-15")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("건별 정산·지급보류 내역은 최대 1개월까지 조회할 수 있습니다."))
			.andDo(document("payout-seller-detail-too-long", ERROR_RESPONSE));
		request("/holds", "from", "2026-09-15", "to", "2026-10-15")
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("시작이 끝보다 늦으면 400")
	void reversedPeriod() throws Exception {
		request("/payout-items", "from", "2026-09-30", "to", "2026-09-01")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("조회 시작이 끝보다 늦습니다."))
			.andDo(document("payout-seller-period-reversed", ERROR_RESPONSE));
	}

	@Test
	@DisplayName("기간이 없거나 형식이 틀리면 400")
	void invalidParameters() throws Exception {
		request("/payouts", "fromMonth", "2026-01")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("필수 요청 값이 없습니다."))
			.andDo(document("payout-seller-missing-period", ERROR_RESPONSE));
		request("/payouts", "fromMonth", "2026/01", "toMonth", "2026-02")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다."));
	}

	@Test
	@DisplayName("회원 ID 헤더가 없으면 401")
	void missingMemberId() throws Exception {
		mockMvc.perform(get(URL + "/summary"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"))
			.andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
			.andDo(document("payout-seller-unauthorized", ERROR_RESPONSE));
	}

	@Test
	@DisplayName("네 조회 API 모두 회원 ID가 0 이하면 400")
	void invalidMemberId() throws Exception {
		String[] paths = {
			"/summary",
			"/payouts?fromMonth=2025-11&toMonth=2026-10",
			"/payout-items?from=2026-09-01&to=2026-09-30",
			"/holds?from=2026-09-15&to=2026-10-14"
		};
		for (String path : paths) {
			mockMvc.perform(get(URL + path).header("X-Member-Id", 0))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value("회원 ID가 올바르지 않습니다."));
		}
	}

	@Test
	@DisplayName("회원 ID와 같은 번호의 판매자가 있어도, 그 회원이 판매자가 아니면 403이고 남의 정산을 보여 주지 않는다")
	void notSellerIsForbidden() throws Exception {
		given(sellerLookupPort.findSellerOf(NOT_SELLER_MEMBER_ID))
			.willReturn(Optional.of(new SellerOfMember(null, null)));

		mockMvc.perform(get(URL + "/payouts").header("X-Member-Id", NOT_SELLER_MEMBER_ID)
				.param("fromMonth", "2025-11").param("toMonth", "2026-10"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("승인된 판매자만 정산을 조회할 수 있습니다."))
			.andDo(document("payout-seller-forbidden", ERROR_RESPONSE));
	}

	@Test
	@DisplayName("네 조회 API 모두 판매자 신청이 대기 · 반려 상태면 403")
	void notApprovedIsForbidden() throws Exception {
		for (SellerStatus status : new SellerStatus[] {SellerStatus.PENDING, SellerStatus.REJECTED}) {
			given(sellerLookupPort.findSellerOf(MEMBER_ID))
				.willReturn(Optional.of(new SellerOfMember(SELLER_ID, status)));
			for (String path : allPaths()) {
				mockMvc.perform(get(URL + path).header("X-Member-Id", MEMBER_ID))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.message").value("승인된 판매자만 정산을 조회할 수 있습니다."));
			}
		}
	}

	@Test
	@DisplayName("존재하지 않거나 탈퇴한 회원이면 404")
	void withdrawnMemberIsNotFound() throws Exception {
		given(sellerLookupPort.findSellerOf(MEMBER_ID)).willReturn(Optional.empty());

		request("/summary")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("존재하지 않는 회원입니다."))
			.andDo(document("payout-seller-member-not-found", ERROR_RESPONSE));
	}

	@Test
	@DisplayName("회원 서비스에서 판매자 정보를 확인하지 못하면 503 (빈 목록으로 답하지 않는다)")
	void lookupUnavailable() throws Exception {
		given(sellerLookupPort.findSellerOf(MEMBER_ID)).willThrow(new PayoutSellerLookupUnavailableException());

		request("/holds", "from", "2026-09-15", "to", "2026-10-14")
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.message").value("판매자 정보를 확인하지 못했습니다. 잠시 뒤 다시 시도해주세요."))
			.andDo(document("payout-seller-lookup-unavailable", ERROR_RESPONSE));
	}

	private String[] allPaths() {
		return new String[] {
			"/summary",
			"/payouts?fromMonth=2025-11&toMonth=2026-10",
			"/payout-items?from=2026-09-01&to=2026-09-30",
			"/holds?from=2026-09-15&to=2026-10-14"
		};
	}
}
