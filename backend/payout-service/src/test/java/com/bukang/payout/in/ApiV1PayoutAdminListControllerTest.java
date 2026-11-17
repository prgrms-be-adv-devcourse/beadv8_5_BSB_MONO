package com.bukang.payout.in;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bukang.payout.app.PayoutHoldUseCase;
import com.bukang.payout.app.PayoutRaceCancelUseCase;
import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutHoldReason;
import com.bukang.payout.dto.PayoutHoldDto;
import com.bukang.payout.out.MemberAdminPort;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.support.RestDocsConfig;

/**
 * 관리자 지급보류 목록 · 정산 목록 (BUK-168). 통과한 테스트만 REST Docs 스니펫을 남긴다
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Import(RestDocsConfig.class)
@ActiveProfiles("test")
class ApiV1PayoutAdminListControllerTest {
	private static final long ADMIN_ID = 99;
	private static final long MEMBER_ID = 8;
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
	private static final BigDecimal FEE_RATE = new BigDecimal("0.0550");

	private static final HeaderDescriptor MEMBER_ID_HEADER =
		headerWithName("X-Member-Id").description("요청한 관리자 회원 ID (게이트웨이가 Access Token에서 꺼내 넣는다)");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutHoldRepository payoutHoldRepository;

	@Autowired
	private PayoutHoldUseCase payoutHoldUseCase;

	@Autowired
	private PayoutRaceCancelUseCase payoutRaceCancelUseCase;

	@MockitoBean
	private MemberAdminPort memberAdminPort;

	// 11/20 지급: 판매자 1(보류 2개, 지급보류), 판매자 2(지급 예정). 12/20 지급: 판매자 3(보류를 걸었다가 이번 달에 풂)
	private Payout heldPayout;
	private Payout calculatedPayout;
	private PayoutHoldDto accountHold;
	private PayoutHoldDto cancelHold;
	private PayoutHoldDto releasedHold;

	@BeforeEach
	void setUp() {
		given(memberAdminPort.isAdmin(ADMIN_ID)).willReturn(true);

		heldPayout = savePayout(1, 10, LocalDate.of(2026, 10, 17), 100_000);
		calculatedPayout = savePayout(2, 20, LocalDate.of(2026, 10, 18), 50_000);
		Payout releasedPayout = savePayout(3, 30, LocalDate.of(2026, 11, 1), 30_000);

		LocalDateTime now = LocalDateTime.now(ZONE);
		releasedHold = payoutHoldUseCase.hold(releasedPayout.getId(), PayoutHoldReason.정산_계좌_미등록, null, ADMIN_ID,
			now.minusMinutes(3));
		accountHold = payoutHoldUseCase.hold(heldPayout.getId(), PayoutHoldReason.정산_계좌_미등록, "계좌 확인 중",
			ADMIN_ID, now.minusMinutes(2));
		cancelHold = payoutHoldUseCase.hold(heldPayout.getId(), PayoutHoldReason.대회_취소_처리, null, ADMIN_ID,
			now.minusMinutes(1));
		payoutHoldUseCase.release(releasedHold.getId(), ADMIN_ID, now);
	}

	@AfterEach
	void tearDown() {
		payoutHoldRepository.deleteAll();
		payoutRepository.deleteAll();
	}

	private Payout savePayout(long sellerId, long raceId, LocalDate raceDate, long price) {
		Payout payout = Payout.finalStage(sellerId, raceId, "테스트 대회", raceDate, raceDate.plusDays(1).atTime(3, 0),
			new BusinessDayCalendar());
		payout.addItem(raceId * 100, price, FEE_RATE, raceDate.plusDays(1));
		Payout saved = payoutRepository.save(payout);
		saved.assignPayoutCode();
		return payoutRepository.save(saved);
	}

	@Test
	@DisplayName("보류 목록을 최근 보류부터 돌려주고, 카드는 보류 중 건수 · 정산마다 한 번 더한 보류 금액 · 이번 달 해제 건수다")
	void holds() throws Exception {
		mockMvc.perform(get("/api/v1/payout/admin/holds").header("X-Member-Id", ADMIN_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("지급보류 목록을 조회했습니다."))
			.andExpect(jsonPath("$.data.summary.heldCount").value(2))
			// 보류 2개가 걸린 정산의 지급액을 한 번만 센다 (11/17 결정)
			.andExpect(jsonPath("$.data.summary.heldAmount").value(heldPayout.getPayoutAmount()))
			.andExpect(jsonPath("$.data.summary.releasedThisMonthCount").value(1))
			.andExpect(jsonPath("$.data.holds.totalElements").value(3))
			.andExpect(jsonPath("$.data.holds.content[*].holdCode").value(contains(
				cancelHold.getHoldCode(), accountHold.getHoldCode(), releasedHold.getHoldCode())))
			.andExpect(jsonPath("$.data.holds.content[0].id").value(cancelHold.getId()))
			.andExpect(jsonPath("$.data.holds.content[0].sellerId").value(1))
			.andExpect(jsonPath("$.data.holds.content[0].payoutId").value(heldPayout.getId()))
			.andExpect(jsonPath("$.data.holds.content[0].payoutCode").value(heldPayout.getPayoutCode()))
			.andExpect(jsonPath("$.data.holds.content[0].payoutAmount").value(heldPayout.getPayoutAmount()))
			.andExpect(jsonPath("$.data.holds.content[0].reasonType").value("대회_취소_처리"))
			.andExpect(jsonPath("$.data.holds.content[0].status").value("지급보류"))
			.andExpect(jsonPath("$.data.holds.content[2].status").value("해제"))
			.andDo(document("payout-admin-holds",
				requestHeaders(MEMBER_ID_HEADER),
				queryParameters(
					parameterWithName("status").description("보류 상태 (지급보류, 해제, 취소). 없으면 전체").optional(),
					parameterWithName("code").description("지급보류 ID(HD-) 또는 정산 ID(ST-). 주면 상태는 보지 않는다")
						.optional(),
					parameterWithName("page").description("페이지 번호 (0부터, 기본 0)").optional(),
					parameterWithName("size").description("페이지 크기 (1 ~ 100, 기본 50)").optional()
				),
				relaxedResponseFields(
					fieldWithPath("data.summary.heldCount").description("카드: 보류 중인 지급보류 수 (필터와 상관없음)"),
					fieldWithPath("data.summary.heldAmount")
						.description("카드: 보류 중인 정산의 지급액 합. 보류가 여럿 걸린 정산도 한 번만 더한다"),
					fieldWithPath("data.summary.releasedThisMonthCount").description("카드: 이번 달(한국 시간)에 푼 지급보류 수"),
					fieldWithPath("data.holds.content[].id").description("지급보류 ID (보류 해제 API 경로에 쓴다)"),
					fieldWithPath("data.holds.content[].holdCode").description("지급보류 ID 표기 (HD-)"),
					fieldWithPath("data.holds.content[].sellerId").description("판매자 ID"),
					fieldWithPath("data.holds.content[].payoutId").description("정산 내역 ID"),
					fieldWithPath("data.holds.content[].payoutCode").description("정산 ID 표기 (ST-)"),
					fieldWithPath("data.holds.content[].payoutAmount").description("보류된 정산의 지급액"),
					fieldWithPath("data.holds.content[].reasonType").description("보류 사유"),
					fieldWithPath("data.holds.content[].reason").description("사유 상세").optional()
						.type(JsonFieldType.STRING),
					fieldWithPath("data.holds.content[].status").description("보류 상태 (지급보류, 해제, 취소: 대회 취소로 정산과 함께 닫힘)"),
					fieldWithPath("data.holds.content[].heldAt").description("보류 일시"),
					fieldWithPath("data.holds.content[].releasedAt").description("해제 일시 (보류 중에는 null)").optional()
						.type(JsonFieldType.STRING)
				)));
	}

	@Test
	@DisplayName("상태를 고르면 목록은 그 상태만, 카드는 전체로 센다")
	void holdsByStatus() throws Exception {
		mockMvc.perform(get("/api/v1/payout/admin/holds").header("X-Member-Id", ADMIN_ID).param("status", "해제"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.summary.heldCount").value(2))
			.andExpect(jsonPath("$.data.holds.totalElements").value(1))
			.andExpect(jsonPath("$.data.holds.content[0].holdCode").value(releasedHold.getHoldCode()));
	}

	@Test
	@DisplayName("HD- 보류 ID로 그 보류 한 건을 찾는다")
	void holdsByHoldCode() throws Exception {
		mockMvc.perform(get("/api/v1/payout/admin/holds").header("X-Member-Id", ADMIN_ID)
				.param("code", accountHold.getHoldCode().toLowerCase()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.holds.totalElements").value(1))
			.andExpect(jsonPath("$.data.holds.content[0].id").value(accountHold.getId()));
	}

	@Test
	@DisplayName("ST- 정산 ID로 그 정산에 걸린 보류를 모두 찾는다 (상태는 보지 않는다)")
	void holdsByPayoutCode() throws Exception {
		mockMvc.perform(get("/api/v1/payout/admin/holds").header("X-Member-Id", ADMIN_ID)
				.param("code", " " + heldPayout.getPayoutCode() + " ").param("status", "해제"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.holds.totalElements").value(2))
			.andExpect(jsonPath("$.data.holds.content[*].id").value(containsInAnyOrder(
				(int)accountHold.getId(), (int)cancelHold.getId())));
	}

	@Test
	@DisplayName("관리자가 아니면 보류 목록을 403으로 막는다")
	void holdsNotAdmin() throws Exception {
		mockMvc.perform(get("/api/v1/payout/admin/holds").header("X-Member-Id", MEMBER_ID))
			.andExpect(status().isForbidden())
			.andDo(document("payout-admin-holds-forbidden"));
	}

	@Test
	@DisplayName("지급일의 정산과 상태별 건수 · 지급액 합, 줄마다 진행 중 보류를 돌려준다")
	void payouts() throws Exception {
		mockMvc.perform(get("/api/v1/payout/admin/payouts").header("X-Member-Id", ADMIN_ID)
				.param("payDate", "2026-11-20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("정산 목록을 조회했습니다."))
			.andExpect(jsonPath("$.data.payDate").value("2026-11-20"))
			.andExpect(jsonPath("$.data.summary[?(@.status == '지급보류')].count").value(contains(1)))
			.andExpect(jsonPath("$.data.summary[?(@.status == '지급보류')].payoutAmount")
				.value(contains((int)heldPayout.getPayoutAmount())))
			.andExpect(jsonPath("$.data.summary[?(@.status == '지급_예정')].count").value(contains(1)))
			.andExpect(jsonPath("$.data.summary[?(@.status == '지급_완료')].count").value(contains(0)))
			.andExpect(jsonPath("$.data.payouts.totalElements").value(2))
			.andExpect(jsonPath("$.data.payouts.content[0].payout.id").value(heldPayout.getId()))
			.andExpect(jsonPath("$.data.payouts.content[0].sellerId").value(1))
			.andExpect(jsonPath("$.data.payouts.content[0].holds[*].id").value(containsInAnyOrder(
				(int)accountHold.getId(), (int)cancelHold.getId())))
			.andExpect(jsonPath("$.data.payouts.content[1].payout.id").value(calculatedPayout.getId()))
			.andExpect(jsonPath("$.data.payouts.content[1].holds").isEmpty())
			.andDo(document("payout-admin-payouts",
				requestHeaders(MEMBER_ID_HEADER),
				queryParameters(
					parameterWithName("payDate").description("지급 예정일 (yyyy-MM-dd). 없으면 오늘보다 뒤인 가장 가까운 20일")
						.optional(),
					parameterWithName("status").description("정산 상태. 없으면 그날의 모든 상태").optional(),
					parameterWithName("page").description("페이지 번호 (0부터, 기본 0)").optional(),
					parameterWithName("size").description("페이지 크기 (1 ~ 100, 기본 50)").optional()
				),
				relaxedResponseFields(
					fieldWithPath("data.payDate").description("조회한 지급 예정일"),
					fieldWithPath("data.summary[].status").description("카드: 정산 상태 (모든 상태가 0건이어도 나온다)"),
					fieldWithPath("data.summary[].count").description("카드: 그 상태의 정산 수 (상태 선택과 상관없음)"),
					fieldWithPath("data.summary[].payoutAmount").description("카드: 그 상태의 지급액 합"),
					fieldWithPath("data.payouts.content[].payout").description("정산 내역 (판매자 정산 조회와 같은 모양)"),
					fieldWithPath("data.payouts.content[].sellerId").description("판매자 ID"),
					fieldWithPath("data.payouts.content[].holds[]")
						.description("진행 중인 지급보류. id로 보류 해제를 부른다. 없으면 빈 목록"),
					fieldWithPath("data.payouts.content[].holds[].id").description("지급보류 ID"),
					fieldWithPath("data.payouts.content[].holds[].holdCode").description("지급보류 ID 표기 (HD-)"),
					fieldWithPath("data.payouts.content[].holds[].reasonType").description("보류 사유")
				)));
	}

	@Test
	@DisplayName("상태를 고르면 목록은 그 상태만, 카드는 그날 전체로 센다")
	void payoutsByStatus() throws Exception {
		mockMvc.perform(get("/api/v1/payout/admin/payouts").header("X-Member-Id", ADMIN_ID)
				.param("payDate", "2026-11-20").param("status", "지급_예정"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.summary[?(@.status == '지급보류')].count").value(contains(1)))
			.andExpect(jsonPath("$.data.payouts.totalElements").value(1))
			.andExpect(jsonPath("$.data.payouts.content[0].payout.id").value(calculatedPayout.getId()));
	}

	@Test
	@DisplayName("지급일을 빼면 오늘보다 뒤인 가장 가까운 20일을 조회하고 응답에 넣는다")
	void payoutsDefaultPayDate() throws Exception {
		LocalDate expected = Payout.nextPayDayAfter(LocalDate.now(ZONE));

		mockMvc.perform(get("/api/v1/payout/admin/payouts").header("X-Member-Id", ADMIN_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.payDate").value(expected.toString()));
	}

	@Test
	@DisplayName("대회가 취소된 정산의 보류는 닫혀 카드 두 숫자와 정산 목록의 진행 중 보류에서 빠진다 (PR 569 리뷰)")
	void canceledRaceHoldIsClosed() throws Exception {
		Payout canceled = savePayout(4, 40, LocalDate.of(2026, 10, 17), 70_000);
		payoutHoldUseCase.hold(canceled.getId(), PayoutHoldReason.정산_계좌_미등록, null, ADMIN_ID,
			LocalDateTime.now(ZONE));
		payoutRaceCancelUseCase.cancelRace(40, LocalDateTime.now(ZONE));

		mockMvc.perform(get("/api/v1/payout/admin/holds").header("X-Member-Id", ADMIN_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.summary.heldCount").value(2))
			.andExpect(jsonPath("$.data.summary.heldAmount").value(heldPayout.getPayoutAmount()))
			.andExpect(jsonPath("$.data.summary.releasedThisMonthCount").value(1))
			.andExpect(jsonPath("$.data.holds.content[0].status").value("취소"));
		mockMvc.perform(get("/api/v1/payout/admin/payouts").header("X-Member-Id", ADMIN_ID)
				.param("payDate", "2026-11-20").param("status", "취소"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.payouts.content[0].payout.id").value(canceled.getId()))
			.andExpect(jsonPath("$.data.payouts.content[0].holds").isEmpty());
	}

	@Test
	@DisplayName("관리자가 아니면 정산 목록을 403으로 막는다")
	void payoutsNotAdmin() throws Exception {
		mockMvc.perform(get("/api/v1/payout/admin/payouts").header("X-Member-Id", MEMBER_ID))
			.andExpect(status().isForbidden());
	}
}
