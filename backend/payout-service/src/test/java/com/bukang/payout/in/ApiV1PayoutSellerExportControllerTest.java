package com.bukang.payout.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutExportType;
import com.bukang.payout.domain.PayoutSellerExportLog;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.PayoutSellerExportLogRepository;
import com.bukang.payout.out.SellerLookupPort;
import com.bukang.payout.out.SellerLookupPort.SellerOfMember;

import jakarta.persistence.EntityManager;

// 정산 자료 CSV 내려받기. 조회 API와 같은 값·순서·기간 한도로 담고, 본인 것만, 내려받기를 기록하는지 확인한다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiV1PayoutSellerExportControllerTest {
	private static final long SELLER_ID = 1;
	private static final long MEMBER_ID = 7;
	private static final BigDecimal FEE_RATE = new BigDecimal("0.0550");
	private static final BusinessDayCalendar CALENDAR = new BusinessDayCalendar();
	private static final String URL = "/api/v1/payout/seller";
	// 엑셀이 UTF-8로 읽게 맨 앞에 붙는 BOM
	private static final String BOM = "﻿";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutSellerExportLogRepository exportLogRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private EntityManager entityManager;

	@MockitoBean
	private SellerLookupPort sellerLookupPort;

	// 판매자 1: 2026-09 정산(참가권 2장, 주문 상품 100·101), 2026-08 정산(참가권 1장)
	@BeforeEach
	void setUp() {
		given(sellerLookupPort.findSellerOf(MEMBER_ID))
			.willReturn(Optional.of(new SellerOfMember(SELLER_ID, SellerStatus.APPROVED)));

		transactionTemplate.executeWithoutResult(status -> {
			Payout september = savePayout(10, LocalDate.of(2026, 9, 26));
			september.addItem(100, 50_000L, FEE_RATE, LocalDate.of(2026, 9, 26));
			september.addItem(101, 50_000L, FEE_RATE, LocalDate.of(2026, 9, 26));
			savePayout(11, LocalDate.of(2026, 8, 16)).addItem(200, 30_000L, FEE_RATE, LocalDate.of(2026, 8, 16));
		});
	}

	@AfterEach
	void tearDown() {
		exportLogRepository.deleteAll();
		payoutRepository.deleteAll();
	}

	private Payout savePayout(long raceId, LocalDate targetDate) {
		Payout payout = Payout.finalStage(SELLER_ID, raceId, "대회 %d".formatted(raceId), targetDate.minusDays(1),
			targetDate.atTime(3, 0), CALENDAR);
		entityManager.persist(payout);
		entityManager.flush();
		payout.assignPayoutCode();
		return payout;
	}

	private String body(MvcResult result) throws Exception {
		return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
	}

	@Test
	@DisplayName("정산 내역 CSV: 조회와 같은 값을 최근 달부터, BOM이 붙은 엑셀용 CSV로 모두 담고 내려받기를 기록한다")
	void exportPayouts() throws Exception {
		MvcResult result = mockMvc.perform(post(URL + "/payouts/export").header("X-Member-Id", MEMBER_ID)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"fromMonth\":\"2025-11\",\"toMonth\":\"2026-10\"}"))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
			.andExpect(header().string("Content-Disposition", "attachment; filename=\"payouts-2025-11-2026-10.csv\""))
			.andReturn();

		String csv = body(result);
		assertThat(csv).startsWith(BOM + "정산 ID,대회 ID,대회명,정산 단계,정산 월");
		String[] lines = csv.split("\r\n");
		// 머리줄 + 2개월(최근 달 먼저)
		assertThat(lines).hasSize(3);
		assertThat(lines[1]).contains("대회 10", "최종_정산", "2026-09", "100000", "5500", "94500", "지급_예정");
		assertThat(lines[2]).contains("대회 11", "2026-08");

		List<PayoutSellerExportLog> logs = exportLogRepository.findBySellerIdOrderByIdDesc(SELLER_ID);
		assertThat(logs).singleElement().satisfies(log -> {
			assertThat(log.getMemberId()).isEqualTo(MEMBER_ID);
			assertThat(log.getExportType()).isEqualTo(PayoutExportType.PAYOUTS);
			assertThat(log.getRowCount()).isEqualTo(2);
		});
	}

	@Test
	@DisplayName("건별 정산 CSV: 정산 생성일 기간의 건별 정산을 페이지 없이 최근 순으로 모두 담고 내려받기를 기록한다")
	void exportPayoutItems() throws Exception {
		MvcResult result = mockMvc.perform(post(URL + "/payout-items/export").header("X-Member-Id", MEMBER_ID)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"from\":\"2026-09-01\",\"to\":\"2026-09-30\"}"))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
			.andReturn();

		String csv = body(result);
		assertThat(csv).startsWith(BOM + "정산 항목 ID,주문 항목 ID,원장 종류,금액");
		String[] lines = csv.split("\r\n");
		// 머리줄 + 참가권 2장(주문 상품 101이 100보다 최근). 8월 참가권은 들어가지 않는다
		assertThat(lines).hasSize(3);
		assertThat(lines[1]).contains(",101,", "판매", "50000", "2750", "2026-09-26", "지급_예정");
		assertThat(lines[2]).contains(",100,");

		assertThat(exportLogRepository.findBySellerIdOrderByIdDesc(SELLER_ID))
			.singleElement()
			.satisfies(log -> assertThat(log.getExportType()).isEqualTo(PayoutExportType.PAYOUT_ITEMS));
	}

	@Test
	@DisplayName("정산 내역을 12개월 넘게 요청하면 400이고 내려받기를 기록하지 않는다")
	void exportPayoutsTooLong() throws Exception {
		mockMvc.perform(post(URL + "/payouts/export").header("X-Member-Id", MEMBER_ID)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"fromMonth\":\"2025-11\",\"toMonth\":\"2026-11\"}"))
			.andExpect(status().isBadRequest());

		assertThat(exportLogRepository.findBySellerIdOrderByIdDesc(SELLER_ID)).isEmpty();
	}

	@Test
	@DisplayName("승인된 판매자가 아니면 403이고 내려받기를 기록하지 않는다")
	void notApprovedIsForbidden() throws Exception {
		given(sellerLookupPort.findSellerOf(MEMBER_ID))
			.willReturn(Optional.of(new SellerOfMember(SELLER_ID, SellerStatus.PENDING)));

		mockMvc.perform(post(URL + "/payout-items/export").header("X-Member-Id", MEMBER_ID)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"from\":\"2026-09-01\",\"to\":\"2026-09-30\"}"))
			.andExpect(status().isForbidden());

		assertThat(exportLogRepository.findBySellerIdOrderByIdDesc(SELLER_ID)).isEmpty();
	}
}
