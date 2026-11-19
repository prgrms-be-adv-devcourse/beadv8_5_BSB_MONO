package com.bukang.payout.app;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.PayoutExportType;
import com.bukang.payout.domain.PayoutQueryPeriod;
import com.bukang.payout.domain.PayoutSellerExportLog;
import com.bukang.payout.dto.PayoutDto;
import com.bukang.payout.dto.PayoutItemDto;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.PayoutSellerExportLogRepository;

import lombok.RequiredArgsConstructor;

/**
 * 판매자 정산 자료(정산 내역·건별 정산)를 CSV로 내려받는다 (정산 화면 「엑셀로 내려받기」)
 * 조회 API(GET payouts·payout-items)와 같은 쿼리·기간 한도·정렬을 그대로 쓰고, 페이지 없이 기간 안의 줄을 모두 담는다
 * 개인정보는 아니지만 누가 언제 무엇을 몇 줄 받았는지 같은 트랜잭션에서 기록한다. 자기 판매자 ID로만 조회한다
 * shortcut: 한 번에 메모리에서 만든다. 기간 한도(정산 12개월·건별 1개월)가 막아 주고, 더 커지면 나눠 쓰기(스트리밍)로 바꾼다
 */
@Service
@RequiredArgsConstructor
public class PayoutSellerExportUseCase {
	// enum 값은 JSON과 같게 name()으로 찍는다(일차_정산, 지급_예정 등). 칸 이름은 읽기 쉽게 한글로 둔다
	private static final List<String> PAYOUTS_HEADER = List.of("정산 ID", "대회 ID", "대회명", "정산 단계", "정산 월",
		"판매액", "환불액", "수수료", "선지급 차감", "지급액", "상태", "정산 확정 시각", "지급 예정일", "지급 완료 시각");
	private static final List<String> ITEMS_HEADER = List.of("정산 항목 ID", "주문 항목 ID", "원장 종류", "금액",
		"수수료율", "수수료", "정산 생성일", "정산 ID", "대회 ID", "대회명", "상태", "지급 예정일");

	private final PayoutRepository payoutRepository;
	private final PayoutItemRepository payoutItemRepository;
	private final PayoutSellerExportLogRepository exportLogRepository;

	// 조회 API payouts와 같은 값: 정산 월이 기간 안인 정산 내역을 최근 달부터, 최대 12개월
	@Transactional
	public PayoutExportFile exportPayouts(long sellerId, long memberId, YearMonth fromMonth, YearMonth toMonth) {
		PayoutQueryPeriod.checkPayoutMonths(fromMonth, toMonth);
		List<List<String>> lines = payoutRepository
			.findBySellerIdAndPayoutMonthBetweenOrderByPayoutMonthDescIdDesc(
				sellerId, fromMonth.toString(), toMonth.toString())
			.stream()
			.map(PayoutDto::from)
			.map(PayoutSellerExportUseCase::payoutLine)
			.toList();
		exportLogRepository.save(PayoutSellerExportLog.of(sellerId, memberId, PayoutExportType.PAYOUTS, lines.size()));
		return new PayoutExportFile("payouts-%s-%s.csv".formatted(fromMonth, toMonth),
			PayoutExportCsv.write(PAYOUTS_HEADER, lines));
	}

	// 조회 API payout-items와 같은 값: 정산 생성일이 기간 안인 건별 정산을 최근 날부터, 최대 1개월. 페이지 없이 모두 담는다
	@Transactional
	public PayoutExportFile exportPayoutItems(long sellerId, long memberId, LocalDate from, LocalDate to) {
		PayoutQueryPeriod.checkDetailDays(from, to);
		List<List<String>> lines = payoutItemRepository.findSellerItems(sellerId, from, to, Pageable.unpaged())
			.getContent()
			.stream()
			.map(PayoutItemDto::from)
			.map(PayoutSellerExportUseCase::itemLine)
			.toList();
		exportLogRepository.save(
			PayoutSellerExportLog.of(sellerId, memberId, PayoutExportType.PAYOUT_ITEMS, lines.size()));
		return new PayoutExportFile("payout-items-%s-%s.csv".formatted(from, to),
			PayoutExportCsv.write(ITEMS_HEADER, lines));
	}

	private static List<String> payoutLine(PayoutDto payout) {
		return List.of(payout.getPayoutCode(), String.valueOf(payout.getRaceId()), nullToEmpty(payout.getRaceName()),
			payout.getPayoutStage().name(), payout.getPayoutMonth(), String.valueOf(payout.getSalesAmount()),
			String.valueOf(payout.getRefundAmount()), String.valueOf(payout.getFeeAmount()),
			String.valueOf(payout.getAdvanceDeductionAmount()), String.valueOf(payout.getPayoutAmount()),
			payout.getStatus().name(), nullToEmpty(payout.getSettledAt()), nullToEmpty(payout.getScheduledPayDate()),
			nullToEmpty(payout.getPaidAt()));
	}

	private static List<String> itemLine(PayoutItemDto item) {
		return List.of(String.valueOf(item.getId()),
			item.getOrderItemId() == null ? "" : String.valueOf(item.getOrderItemId()),
			item.getRecordType().name(), String.valueOf(item.getAmount()), item.getFeeRate().toPlainString(),
			String.valueOf(item.getFeeAmount()), nullToEmpty(item.getTargetDate()), item.getPayoutCode(),
			String.valueOf(item.getRaceId()), nullToEmpty(item.getRaceName()), item.getPayoutStatus().name(),
			nullToEmpty(item.getScheduledPayDate()));
	}

	private static String nullToEmpty(Object value) {
		return value == null ? "" : value.toString();
	}
}
