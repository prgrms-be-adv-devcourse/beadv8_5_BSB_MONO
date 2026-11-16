package com.bukang.ai.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import com.bukang.ai.domain.AiSource;
import com.bukang.ai.dto.AiListDto;
import com.bukang.ai.out.ops.OpsPort;
import com.bukang.ai.support.StubOpsPort;

import tools.jackson.databind.ObjectMapper;

// 도구를 모델 없이 직접 부른다. 다른 서비스는 대역(StubOpsPort)이다
class AiToolsTest {
	private static final long SELLER = 3L;

	private final StubOpsPort ops = new StubOpsPort();
	private final SellerTools sellerTools = new SellerTools(ops);
	private final AdminTools adminTools = new AdminTools(ops);
	private final AiToolTrace trace = new AiToolTrace(SELLER);
	private final ToolContext toolContext = new ToolContext(Map.of(AiToolTrace.KEY, trace));
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@DisplayName("도구는 로그인 정보의 회원 번호로만 조회한다")
	void usesLoginMemberId() {
		sellerTools.sellerKitsWaitingForTracking(toolContext);
		sellerTools.sellerNextPayout(toolContext);
		sellerTools.sellerPayoutHoldReasons(toolContext);
		sellerTools.sellerMonthlySales(toolContext);
		adminTools.adminWaitingInquiries(toolContext);
		adminTools.adminPendingSellers(toolContext);

		assertThat(ops.calledMemberIds).isNotEmpty().containsOnly(SELLER);
	}

	@Test
	@DisplayName("송장 입력 대기는 배송 준비에서 환불 기한 전을 뺀 건수이고, 목록은 응답용으로만 담고 모델에는 이름을 보내지 않는다")
	void kits() throws Exception {
		ops.kitSummary = new OpsPort.KitSummary(7, 0);
		ops.kitPage = new OpsPort.KitPage(List.of(
			new OpsPort.Kit("ORD-1", "김민지", "10km"), new OpsPort.Kit("ORD-2", "박서준", "하프")), 7);

		Object result = sellerTools.sellerKitsWaitingForTracking(toolContext);

		assertThat(result).isEqualTo(new SellerTools.KitsResult(7, 0, 2, 5));
		assertThat(objectMapper.writeValueAsString(result)).doesNotContain("김민지", "박서준", "ORD-1");
		assertThat(trace.list()).isEqualTo(new AiListDto(List.of(
			new AiListDto.Row("ORD-1", "김민지", "10km"), new AiListDto.Row("ORD-2", "박서준", "하프")), 5));
		assertThat(trace.sources()).singleElement().satisfies(source -> {
			assertThat(source.screen()).isEqualTo(AiSource.SELLER_KITS.getScreen());
			assertThat(source.path()).isEqualTo("/seller/kits");
			assertThat(source.asOf()).isNotNull();
		});
	}

	@Test
	@DisplayName("환불 기한 전 건이 섞여 있으면 목록을 붙이지 않는다 (대기 건만 가를 수 없다)")
	void kitsWithoutList() {
		ops.kitSummary = new OpsPort.KitSummary(7, 3);
		ops.kitPage = new OpsPort.KitPage(List.of(new OpsPort.Kit("ORD-1", "김민지", "10km")), 7);

		assertThat(sellerTools.sellerKitsWaitingForTracking(toolContext))
			.isEqualTo(new SellerTools.KitsResult(4, 3, 0, 4));
		assertThat(trace.list()).isNull();
	}

	@Test
	@DisplayName("키트 도구를 다시 불렀을 때 실패하면 앞 호출의 목록을 남기지 않는다")
	void kitsListCleared() {
		ops.kitSummary = new OpsPort.KitSummary(1, 0);
		ops.kitPage = new OpsPort.KitPage(List.of(new OpsPort.Kit("ORD-1", "김민지", "10km")), 1);
		sellerTools.sellerKitsWaitingForTracking(toolContext);
		assertThat(trace.list()).isNotNull();

		ops.unavailable = true;
		sellerTools.sellerKitsWaitingForTracking(toolContext);

		assertThat(trace.list()).isNull();
		assertThat(trace.sources()).singleElement().satisfies(source -> assertThat(source.asOf()).isNull());
	}

	@Test
	@DisplayName("배송 준비 6건이 모두 환불 기한 전이면 지금 보낼 0건과 기한 뒤 보낼 6건을 함께 돌려준다")
	void kitsAllBeforeRefundDeadline() {
		ops.kitSummary = new OpsPort.KitSummary(6, 6);

		assertThat(sellerTools.sellerKitsWaitingForTracking(toolContext))
			.isEqualTo(new SellerTools.KitsResult(0, 6, 0, 0));
		assertThat(trace.list()).isNull();
	}

	@Test
	@DisplayName("지급보류 이유는 보류 중인 정산의 풀리지 않은 보류만, 상한까지만 돌려준다")
	void holds() {
		LocalDateTime recent = LocalDateTime.now(AiToolTrace.ZONE).minusDays(1);
		OpsPort.Hold held = new OpsPort.Hold("ST-1", "한강 나이트런", 120000, "정산_계좌_미등록", "계좌를 등록해 주세요",
			recent, null);
		OpsPort.Hold released = new OpsPort.Hold("ST-1", "봄 마라톤", 50000, "대사_불일치", "확인 끝",
			recent, recent);
		ops.heldPayouts = List.of(new OpsPort.HeldPayout("ST-1", recent.minusDays(1)));
		ops.holds = List.of(held, released, held, held, held, held, held);
		ops.payoutSummary = new OpsPort.PayoutSummary(null, 0, 1);

		SellerTools.HoldsResult result = (SellerTools.HoldsResult)sellerTools.sellerPayoutHoldReasons(toolContext);

		assertThat(result.holds()).hasSize(SellerTools.LIST_LIMIT).containsOnly(new SellerTools.HoldLine(
			"한강 나이트런", 120000, "정산_계좌_미등록", "계좌를 등록해 주세요", recent.toLocalDate()));
		assertThat(result.notListedCount()).isEqualTo(1);
		assertThat(result.heldPayoutCount()).isEqualTo(1);
		assertThat(result.olderHeldPayoutCount()).isZero();
	}

	@Test
	@DisplayName("2개월 전에 보류한 정산도 사유를 찾고, 그 정산이 생긴 달까지만 거슬러 본다")
	void oldHold() {
		LocalDateTime twoMonthsAgo = LocalDateTime.now(AiToolTrace.ZONE).minusMonths(2);
		OpsPort.Hold old = new OpsPort.Hold("ST-9", "가을 하프", 300000, "기타", "기상 악화로 하프 종목 중단",
			twoMonthsAgo, null);
		ops.heldPayouts = List.of(new OpsPort.HeldPayout("ST-9", twoMonthsAgo.minusHours(1)));
		ops.holds = List.of(old);
		ops.payoutSummary = new OpsPort.PayoutSummary(null, 0, 1);

		SellerTools.HoldsResult result = (SellerTools.HoldsResult)sellerTools.sellerPayoutHoldReasons(toolContext);

		assertThat(result.holds()).containsExactly(new SellerTools.HoldLine(
			"가을 하프", 300000, "기타", "기상 악화로 하프 종목 중단", twoMonthsAgo.toLocalDate()));
		assertThat(result.olderHeldPayoutCount()).isZero();
		// 이번 달, 지난달, 정산이 생긴 2개월 전 달까지 세 번만 부른다
		assertThat(ops.holdsCalls).hasSize(3);
		// 정산 API의 기간 한도(끝 날 < 시작 날 + 1개월)를 지키고, 달 사이에 빈 날이 없다
		for (int i = 0; i < ops.holdsCalls.size(); i++) {
			LocalDate[] call = ops.holdsCalls.get(i);
			assertThat(call[1]).isBefore(call[0].plusMonths(1));
			if (i > 0) {
				assertThat(call[1].plusDays(1)).isEqualTo(ops.holdsCalls.get(i - 1)[0]);
			}
		}
	}

	@Test
	@DisplayName("한 정산에 사유가 다른 보류가 두 개면 새 보류를 찾은 뒤에도 거슬러 봐 옛 사유까지 돌려준다")
	void twoHoldsOnOnePayout() {
		LocalDateTime now = LocalDateTime.now(AiToolTrace.ZONE);
		OpsPort.Hold weather = new OpsPort.Hold("ST-9", "가을 하프", 300000, "기타", "기상 악화로 하프 종목 중단",
			now.minusMonths(2), null);
		OpsPort.Hold account = new OpsPort.Hold("ST-9", "가을 하프", 300000, "정산_계좌_미등록", "계좌를 등록해 주세요",
			now.minusMinutes(1), null);
		ops.heldPayouts = List.of(new OpsPort.HeldPayout("ST-9", now.minusMonths(2).minusHours(1)));
		ops.holds = List.of(weather, account);
		ops.payoutSummary = new OpsPort.PayoutSummary(null, 0, 1);

		SellerTools.HoldsResult result = (SellerTools.HoldsResult)sellerTools.sellerPayoutHoldReasons(toolContext);

		assertThat(result.holds()).extracting(SellerTools.HoldLine::reason)
			.containsExactly("계좌를 등록해 주세요", "기상 악화로 하프 종목 중단");
	}

	@Test
	@DisplayName("대회 취소로 닫힌 정산에 남은 보류 기록은 보류 중으로 말하지 않는다")
	void cancelledPayoutHoldIgnored() {
		LocalDateTime now = LocalDateTime.now(AiToolTrace.ZONE);
		OpsPort.Hold stale = new OpsPort.Hold("ST-CANCEL", "취소된 대회", 10000, "기타", "대회 취소",
			now.minusMinutes(1), null);
		OpsPort.Hold real = new OpsPort.Hold("ST-9", "가을 하프", 300000, "기타", "기상 악화로 하프 종목 중단",
			now.minusMonths(2), null);
		ops.heldPayouts = List.of(new OpsPort.HeldPayout("ST-9", now.minusMonths(2).minusHours(1)));
		ops.holds = List.of(stale, real);
		ops.payoutSummary = new OpsPort.PayoutSummary(null, 0, 1);

		SellerTools.HoldsResult result = (SellerTools.HoldsResult)sellerTools.sellerPayoutHoldReasons(toolContext);

		assertThat(result.holds()).extracting(SellerTools.HoldLine::raceName).containsExactly("가을 하프");
	}

	@Test
	@DisplayName("보류 중인 정산이 없으면 정산 내역을 조회하지 않고, 12개월보다 오래된 보류 중 정산은 건수로 알린다")
	void heldPayoutsOutsideWindow() {
		sellerTools.sellerPayoutHoldReasons(toolContext);
		assertThat(ops.holdsCalls).isEmpty();

		ops.payoutSummary = new OpsPort.PayoutSummary(null, 0, 2);
		SellerTools.HoldsResult result = (SellerTools.HoldsResult)sellerTools.sellerPayoutHoldReasons(toolContext);

		assertThat(result.holds()).isEmpty();
		assertThat(result.olderHeldPayoutCount()).isEqualTo(2);
		assertThat(ops.holdsCalls).isEmpty();
	}

	@Test
	@DisplayName("승인 대기 판매자는 건수와 가장 오래된 신청일만 돌려준다")
	void pendingSellers() throws Exception {
		ops.pendingRequestedAts = List.of(
			LocalDateTime.of(2026, 11, 5, 10, 0), LocalDateTime.of(2026, 10, 3, 9, 0));

		Object result = adminTools.adminPendingSellers(toolContext);

		assertThat(objectMapper.writeValueAsString(result))
			.isEqualTo("{\"pendingCount\":2,\"oldestRequestedDate\":\"2026-10-03\"}");
	}

	@Test
	@DisplayName("답변 대기 문의는 전체와 관리자 담당 건수를 돌려준다")
	void inquiries() {
		ops.waitingInquiries = 5;
		ops.adminWaitingInquiries = 2;

		assertThat(adminTools.adminWaitingInquiries(toolContext)).isEqualTo(new AdminTools.InquiriesResult(5, 2));
	}

	@Test
	@DisplayName("이번 달 판매액은 그달 1일부터 오늘까지 주문의 판매·취소 합계를 그대로 담고, 주문·참가자 화면을 출처로 남긴다")
	void monthlySales() {
		ops.salesSummary = new OpsPort.SalesSummary(42, 2310000, 3, 165000);

		Object result = sellerTools.monthlySales(trace, LocalDate.of(2026, 11, 16));

		// 취소 건수·환불액도 같은 기간으로 함께 담는다 (BUK-264)
		assertThat(result).isEqualTo(new SellerTools.SalesResult(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 16),
			42, 2310000, 3, 165000));
		assertThat(ops.salesCall).containsExactly(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 16));
		assertThat(trace.sources()).singleElement().satisfies(source -> {
			assertThat(source.screen()).isEqualTo(AiSource.SELLER_SALES.getScreen());
			assertThat(source.path()).isEqualTo("/seller/sales");
			assertThat(source.asOf()).isNotNull();
		});
	}

	@Test
	@DisplayName("월초(1일)에는 그날 하루만, 말일에는 그달 전체를 묻는다. 지난달로 넘어가지 않는다")
	void monthlySalesBoundary() {
		sellerTools.monthlySales(trace, LocalDate.of(2026, 12, 1));
		assertThat(ops.salesCall).containsExactly(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 1));

		sellerTools.monthlySales(trace, LocalDate.of(2026, 2, 28));
		assertThat(ops.salesCall).containsExactly(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
	}

	@Test
	@DisplayName("도구는 한국 날짜의 이번 달을 본다 (서버 시간대와 상관없이)")
	void monthlySalesUsesKoreanDate() {
		LocalDate today = LocalDate.now(AiToolTrace.ZONE);

		sellerTools.sellerMonthlySales(toolContext);

		assertThat(ops.salesCall[1]).isIn(today, LocalDate.now(AiToolTrace.ZONE));
		assertThat(ops.salesCall[0]).isEqualTo(ops.salesCall[1].withDayOfMonth(1));
	}

	@Test
	@DisplayName("판매 합계를 가져오지 못하면 숫자 없이 주문·참가자 화면에서 확인하라고 한다")
	void monthlySalesFailure() {
		ops.unavailable = true;

		assertThat(sellerTools.sellerMonthlySales(toolContext)).isInstanceOfSatisfying(Map.class,
			map -> assertThat((String)map.get("error")).contains(AiSource.SELLER_SALES.getScreen()));
	}

	@Test
	@DisplayName("조회에 실패하면 숫자 없이 화면에서 확인하라는 결과를 돌려주고, 출처는 조회 시각 없이 남긴다")
	void failure() {
		ops.unavailable = true;

		Object result = sellerTools.sellerNextPayout(toolContext);

		assertThat(result).isInstanceOfSatisfying(Map.class, map -> assertThat((String)map.get("error"))
			.contains("숫자를 말하지 말고").contains(AiSource.SELLER_PAYOUT.getScreen()));
		assertThat(trace.sources()).singleElement().satisfies(source -> {
			assertThat(source.asOf()).isNull();
			assertThat(source.path()).isEqualTo("/seller/settlements");
		});
	}
}
