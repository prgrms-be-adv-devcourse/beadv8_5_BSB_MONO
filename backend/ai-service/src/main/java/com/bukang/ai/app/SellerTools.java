package com.bukang.ai.app;

import java.time.LocalDate;
import java.util.List;

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

	private final OpsPort opsPort;

	@Tool(description = "내 송장 입력 대기 키트를 조회한다. waitingCount: 지금 송장을 입력할 수 있는 건수, "
		+ "beforeRefundDeadlineCount: 환불 기한 전이라 아직 보낼 수 없는 건수(대기에 넣지 않는다), "
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

	@Tool(description = "내 다음 정산을 조회한다. nextPayDate: 다음 정산일(null이면 지급 예정 정산이 없다), "
		+ "scheduledPayoutAmount: 그날 받을 정산 예정액(원, 지급보류 제외), heldCount: 지급보류 중인 정산 내역 수")
	public Object sellerNextPayout(ToolContext toolContext) {
		AiToolTrace trace = AiToolTrace.of(toolContext);
		return trace.run(AiSource.SELLER_PAYOUT, () -> opsPort.payoutSummary(trace.memberId()));
	}

	@Tool(description = "내 정산이 지급보류된 이유를 조회한다. heldPayoutCount: 지금 지급보류 중인 정산 내역 수(전체 기간). "
		+ "holds: 최근 1개월 안에 보류되어 아직 풀리지 않은 것의 대회 이름, 보류된 지급액(원), 사유 종류, 사유 글, 보류한 날. "
		+ "notListedCount: 최근 1개월 것 중 여기 담지 않은 나머지 건수. "
		+ "heldPayoutCount가 0보다 큰데 holds에 없는 보류는 1개월보다 오래된 것이니 화면에서 확인하라고 안내한다")
	public Object sellerPayoutHoldReasons(ToolContext toolContext) {
		AiToolTrace trace = AiToolTrace.of(toolContext);
		return trace.run(AiSource.SELLER_HOLDS, () -> {
			LocalDate today = LocalDate.now(AiToolTrace.ZONE);
			// 정산 API의 조회 기간은 최대 1개월이다 (끝 날 < 시작 날 + 1개월). 더 오래된 보류가 있는지는 요약의 건수로 알린다
			List<HoldLine> held = opsPort.holds(trace.memberId(), today.minusMonths(1).plusDays(1), today).stream()
				.filter(hold -> hold.releasedAt() == null)
				.map(hold -> new HoldLine(hold.raceName(), hold.payoutAmount(), hold.reasonType(), hold.reason(),
					hold.heldAt() == null ? null : hold.heldAt().toLocalDate()))
				.toList();
			return new HoldsResult(opsPort.payoutSummary(trace.memberId()).heldCount(),
				held.stream().limit(LIST_LIMIT).toList(), Math.max(0, held.size() - LIST_LIMIT));
		});
	}

	record KitsResult(long waitingCount, long beforeRefundDeadlineCount, int listedCount, long notListedCount) {
	}

	record HoldsResult(long heldPayoutCount, List<HoldLine> holds, int notListedCount) {
	}

	record HoldLine(String raceName, long payoutAmount, String reasonType, String reason, LocalDate heldDate) {
	}
}
