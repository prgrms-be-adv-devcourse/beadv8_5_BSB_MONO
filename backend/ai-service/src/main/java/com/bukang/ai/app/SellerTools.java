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

	@Tool(description = "마지막 질문이 송장 입력 대기 키트(보낼 키트)를 물을 때만 부른다. 답에는 두 숫자를 함께 말한다. "
		+ "sendableNowCount: 환불 기한이 지나 지금 송장을 입력할 수 있는 건수, "
		+ "sendableAfterRefundDeadlineCount: 배송 준비 중이지만 환불 기한 전이라 기한이 지나면 보낼 수 있는 건수. "
		+ "sendableNowCount가 0이어도 sendableAfterRefundDeadlineCount가 있으면 「지금 보낼 키트는 없고, 환불 기한이 지나면 N건을 보낼 수 있다」처럼 답한다. "
		+ "listedCount: 답 아래 목록 카드에 화면이 따로 보여 주는 줄 수(목록 내용은 말하지 않는다), notListedCount: 목록에 없는 나머지 건수")
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

	@Tool(description = "마지막 질문이 다음 정산일·정산 예정액·지급보류 건수를 물을 때만 부른다. nextPayDate: 다음 정산일(null이면 지급 예정 정산이 없다), "
		+ "scheduledPayoutAmount: 그날 받을 정산 예정액(원, 지급보류 제외), heldCount: 지급보류 중인 정산 내역 수")
	public Object sellerNextPayout(ToolContext toolContext) {
		AiToolTrace trace = AiToolTrace.of(toolContext);
		return trace.run(AiSource.SELLER_PAYOUT, () -> opsPort.payoutSummary(trace.memberId()));
	}

	@Tool(description = "마지막 질문이 지급보류된 이유를 물을 때만 부른다. heldPayoutCount: 지금 지급보류 중인 정산 내역 수(전체 기간). "
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

	record KitsResult(long sendableNowCount, long sendableAfterRefundDeadlineCount, int listedCount,
		long notListedCount) {
	}

	record HoldsResult(long heldPayoutCount, List<HoldLine> holds, int notListedCount, long olderHeldPayoutCount) {
	}

	record HoldLine(String raceName, long payoutAmount, String reasonType, String reason, LocalDate heldDate) {
	}
}
