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
	@DisplayName("지급보류 이유는 최근 1개월의 아직 풀리지 않은 보류만, 상한까지만 돌려준다")
	void holds() {
		OpsPort.Hold held = new OpsPort.Hold("한강 나이트런", 120000, "정산_계좌_미등록", "계좌를 등록해 주세요",
			LocalDateTime.of(2026, 11, 3, 9, 0), null);
		OpsPort.Hold released = new OpsPort.Hold("봄 마라톤", 50000, "대사_불일치", "확인 끝",
			LocalDateTime.of(2026, 10, 20, 9, 0), LocalDateTime.of(2026, 10, 25, 9, 0));
		ops.holds = List.of(held, released, held, held, held, held, held);
		ops.payoutSummary = new OpsPort.PayoutSummary(null, 0, 8);

		SellerTools.HoldsResult result = (SellerTools.HoldsResult)sellerTools.sellerPayoutHoldReasons(toolContext);

		assertThat(result.holds()).hasSize(SellerTools.LIST_LIMIT).containsOnly(new SellerTools.HoldLine(
			"한강 나이트런", 120000, "정산_계좌_미등록", "계좌를 등록해 주세요", LocalDate.of(2026, 11, 3)));
		assertThat(result.notListedCount()).isEqualTo(1);
		// 1개월보다 오래된 보류가 있는지 모델이 알 수 있게 전체 보류 건수를 함께 준다
		assertThat(result.heldPayoutCount()).isEqualTo(8);
		// 정산 API의 기간 한도: 끝 날 < 시작 날 + 1개월
		assertThat(ops.holdsTo).isBefore(ops.holdsFrom.plusMonths(1));
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
