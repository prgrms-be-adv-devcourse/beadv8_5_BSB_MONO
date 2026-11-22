package com.bukang.ai.app;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.bukang.ai.domain.AiSource;
import com.bukang.ai.dto.AiListDto;
import com.bukang.ai.out.ops.OpsPort;

import lombok.RequiredArgsConstructor;

/**
 * 판매자 화면의 도구. 판매자 화면 질문에만 붙는다 (AiChatUseCase)
 * 누구 것을 볼지는 모델이 정하지 않는다. 인자가 없고, 회원 번호는 ToolContext(로그인 정보)에서 꺼낸다
 * 돌려주는 값이 모델에 간다. 필요한 값만 담고 구매자 이름·배송지는 담지 않는다
 */
@Component
@RequiredArgsConstructor
public class SellerTools {
	// 목록에 담는 줄 수. 추천값(미확정)
	static final int LIST_LIMIT = 5;
	// 보류 중인 정산을 찾는 정산 월 범위 (이번 달 포함). 정산 내역 조회 한도와 같은 12개월
	static final int HOLD_LOOKBACK_MONTHS = 12;

	private final OpsPort opsPort;

	@Tool(description = "마지막 질문이 송장 입력 대기 키트(보낼 키트)를 물을 때만 부른다"
		+ "(예: 「보낼 키트 있어?」, 「배송 준비 중인 키트 현황 좀」, 「아직 송장 안 넣은 거 있어?」). 답에는 두 숫자를 함께 말한다. "
		+ "sendableNowCount: 환불 기한이 지나 지금 송장을 입력할 수 있는 건수, "
		+ "sendableAfterRefundDeadlineCount: 배송 준비 중이지만 환불 기한 전이라 기한이 지나면 보낼 수 있는 건수. "
		+ "sendableNowCount가 0이어도 sendableAfterRefundDeadlineCount가 있으면 「지금 보낼 키트는 없고, 환불 기한이 지나면 N건을 보낼 수 있다」처럼 답한다. "
		+ "listedCount·notListedCount: 화면 목록 카드용 값이라 답에 쓰지 않는다. "
		+ "배송 중·배송 완료 건수는 이 도구로 알 수 없으므로 그런 질문에는 부르지 않는다")
	public Object sellerKitsWaitingForTracking(ToolContext toolContext) {
		AiToolTrace trace = AiToolTrace.of(toolContext);
		trace.clearList();
		return trace.run(AiSource.SELLER_KITS, () -> {
			OpsPort.KitSummary summary = opsPort.kitSummary(trace.memberId());
			long waiting = Math.max(0, summary.preparing() - summary.beforeRefundDeadline());
			// 목록 API는 배송 준비 전체를 준다. 환불 기한 전 건이 섞여 있으면 대기 건만 가를 수 없어 목록을 붙이지 않는다
			if (waiting == 0 || summary.beforeRefundDeadline() > 0) {
				return new KitsResult(waiting, summary.beforeRefundDeadline(), 0, waiting);
			}
			OpsPort.KitPage page = opsPort.preparingKits(trace.memberId(), LIST_LIMIT);
			List<AiListDto.Row> rows = page.kits().stream().limit(LIST_LIMIT)
				.map(kit -> new AiListDto.Row(kit.orderNo(), kit.participantName(), kit.courseName()))
				.toList();
			long notListed = Math.max(0, page.totalElements() - rows.size());
			trace.list(rows, notListed);
			return new KitsResult(waiting, 0, rows.size(), notListed);
		});
	}

	@Tool(description = "마지막 질문이 이번 달 판매액·판매 장수나 이번 달 취소·환불 현황을 물을 때만 부른다"
		+ "(예: 「이번 달 얼마 팔았어?」, 「환불 요청 들어온 거 있어?」, 「이번 달 취소 몇 건이야?」, 「취소된 주문이 몇 건이야?」). "
		+ "기간 없이 취소 건수를 물으면 이번 달 값이라고 밝히고 답한다. 지난달처럼 이번 달이 아닌 기간은 부르지 않는다. "
		+ "참가자 수·신청자 명단이 아니므로 「참가자 몇 명이 신청했어?」에는 부르지 않는다. from~to: 이번 달 1일부터 오늘까지(한국 날짜). "
		+ "soldCount: 결제일이 이 기간인 참가권 장수, soldAmount: 그 결제 금액 합(원). "
		+ "취소된 참가권은 판매에서 빠지고, 환불을 처리 중인 참가권은 정산처럼 판매로 센다. 정산 예정액이나 입금액이 아니다. "
		+ "canceledCount: 취소를 마친 때가 이 기간인 참가권 장수(대회 취소 환불 포함), refundedAmount: 그 환불액 합(원). "
		+ "환불을 처리 중인 참가권은 아직 취소로 세지 않는다. "
		+ "모든 값은 판매자의 모든 대회를 합친 것이라, 대회 하나의 판매·취소(예: 「OO 대회 몇 장 팔렸어?」)를 물으면 부르지 않는다. "
		+ "그런 질문에는 대회별 판매는 아직 답할 수 없다고 하고 판매 현황(주문 · 참가자) 화면에서 대회를 골라 보라고 안내한다")
	public Object sellerMonthlySales(ToolContext toolContext) {
		return monthlySales(AiToolTrace.of(toolContext), LocalDate.now(AiToolTrace.ZONE));
	}

	// 오늘(한국 날짜)이 속한 달의 1일부터 오늘까지. 1일이면 그날 하루만 본다
	Object monthlySales(AiToolTrace trace, LocalDate today) {
		return trace.run(AiSource.SELLER_SALES, () -> {
			LocalDate from = today.withDayOfMonth(1);
			OpsPort.SalesSummary sales = opsPort.salesSummary(trace.memberId(), from, today);
			return new SalesResult(from, today, sales.soldCount(), sales.soldAmount(), sales.canceledCount(),
				sales.refundedAmount(), SalesResult.ALL_RACES);
		});
	}

	@Tool(description = "마지막 질문이 다음 정산일·정산 예정액·지급보류 건수를 물을 때만 부른다"
		+ "(예: 「돈 언제 들어와?」, 「나 돈 얼마 받을 수 있어?」, 「정산 일정 알려주세요」, "
		+ "「보류된 정산이 있어?」). nextPayDate: 다음 정산일(null이면 지급 예정 정산이 없다), "
		+ "scheduledPayoutAmount: 그날 받을 정산 예정액(원, 지급보류 제외), heldCount: 지급보류 중인 정산 내역 수")
	public Object sellerNextPayout(ToolContext toolContext) {
		AiToolTrace trace = AiToolTrace.of(toolContext);
		return trace.run(AiSource.SELLER_PAYOUT, () -> opsPort.payoutSummary(trace.memberId()));
	}

	@Tool(description = "마지막 질문이 지급보류된 이유나 지급보류 내역을 물을 때만 부른다(예: 「지급보류 내역 보여줘」, 「왜 보류됐어?」). "
		+ "heldPayoutCount: 지금 지급보류 중인 정산 내역 수(전체 기간). "
		+ "holds: 보류한 날과 상관없이 지금 보류 중인 정산의 보류마다 대회 이름, 보류된 지급액(원), 사유 종류, 사유 글, 보류한 날(최근 것부터). "
		+ "정산 하나에 사유가 다른 보류가 여러 개 걸릴 수 있다. notListedCount: 찾았지만 여기 담지 않은 나머지 보류 수. "
		+ "olderHeldPayoutCount: 12개월보다 오래되어 사유를 찾지 못한 보류 중 정산 수. 0보다 크면 화면에서 확인하라고 안내한다")
	public Object sellerPayoutHoldReasons(ToolContext toolContext) {
		AiToolTrace trace = AiToolTrace.of(toolContext);
		return trace.run(AiSource.SELLER_HOLDS, () -> {
			long heldCount = opsPort.payoutSummary(trace.memberId()).heldCount();
			if (heldCount == 0) {
				return new HoldsResult(0, List.of(), 0, 0);
			}
			// 정산 내역 조회 한도(12개월) 안에서 지금 보류 중인 정산을 찾는다. 대회 취소로 닫힌 정산의 남은 보류 기록은 여기서 빠진다
			YearMonth thisMonth = YearMonth.now(AiToolTrace.ZONE);
			List<OpsPort.HeldPayout> payouts = opsPort.heldPayouts(trace.memberId(),
				thisMonth.minusMonths(HOLD_LOOKBACK_MONTHS - 1), thisMonth);
			Set<String> heldCodes = payouts.stream().map(OpsPort.HeldPayout::payoutCode).collect(Collectors.toSet());
			// 보류 내역 API는 보류한 날로 최대 1개월씩만 준다. 보류는 정산 내역이 생긴 뒤에 걸리므로
			// 이번 달부터 가장 먼저 생긴 보류 중 정산의 달까지 한 달씩 거슬러 본다
			YearMonth oldest = payouts.stream().map(payout -> YearMonth.from(payout.settledAt()))
				.min(Comparator.naturalOrder()).orElse(thisMonth.plusMonths(1));
			List<HoldLine> held = new ArrayList<>();
			for (YearMonth month = thisMonth; !month.isBefore(oldest); month = month.minusMonths(1)) {
				LocalDate to = month.equals(thisMonth) ? LocalDate.now(AiToolTrace.ZONE) : month.atEndOfMonth();
				opsPort.holds(trace.memberId(), month.atDay(1), to).stream()
					.filter(hold -> hold.releasedAt() == null && heldCodes.contains(hold.payoutCode()))
					.map(hold -> new HoldLine(hold.raceName(), hold.payoutAmount(), hold.reasonType(), hold.reason(),
						hold.heldAt() == null ? null : hold.heldAt().toLocalDate()))
					.forEach(held::add);
			}
			return new HoldsResult(heldCount, held.stream().limit(LIST_LIMIT).toList(),
				Math.max(0, held.size() - LIST_LIMIT), Math.max(0, heldCount - heldCodes.size()));
		});
	}

	@Tool(description = "마지막 질문이 판매자가 심사할 기록증 건수를 물을 때만 부른다"
		+ "(예: 「심사 대기 몇 건?」, 「심사할 기록증 있어?」, 「기록증 심사 밀린 거 있어?」). "
		+ "waitingCount: 판매자의 심사를 기다리는 기록증 제출 수. 서버가 확인 중인 제출과 이미 승인·승인 불가로 끝난 제출은 세지 않는다")
	public Object sellerRecordCertReviewsWaiting(ToolContext toolContext) {
		AiToolTrace trace = AiToolTrace.of(toolContext);
		return trace.run(AiSource.SELLER_RECORD_CERTS,
			() -> new RecordCertReviewsResult(opsPort.waitingRecordCertReviewCount(trace.memberId())));
	}

	record RecordCertReviewsResult(long waitingCount) {
	}

	record KitsResult(long sendableNowCount, long sendableAfterRefundDeadlineCount, int listedCount,
		long notListedCount) {
	}

	// scope: 모델이 이 합계를 대회 하나의 값처럼 답하지 않게 결과에도 범위를 적는다 (CRU-154)
	record SalesResult(LocalDate from, LocalDate to, long soldCount, long soldAmount, long canceledCount,
		long refundedAmount, String scope) {
		static final String ALL_RACES = "판매자의 모든 대회 합계. 대회 하나의 값이 아니다";
	}

	record HoldsResult(long heldPayoutCount, List<HoldLine> holds, int notListedCount, long olderHeldPayoutCount) {
	}

	record HoldLine(String raceName, long payoutAmount, String reasonType, String reason, LocalDate heldDate) {
	}
}
