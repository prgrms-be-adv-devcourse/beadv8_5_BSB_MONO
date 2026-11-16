package com.bukang.ai.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.bukang.ai.domain.AiScreen;
import com.bukang.ai.out.ops.OpsPort;

// 주문·정산·문의·회원 서비스 대역. 정해 둔 값을 돌려주고, 어느 회원 번호로 불렸는지 남긴다
public class StubOpsPort implements OpsPort {
	public final List<Long> calledMemberIds = new ArrayList<>();
	public KitSummary kitSummary = new KitSummary(0, 0);
	public KitPage kitPage = new KitPage(List.of(), 0);
	public PayoutSummary payoutSummary = new PayoutSummary(null, 0, 0);
	public SalesSummary salesSummary = new SalesSummary(0, 0, 0, 0);
	// salesSummary를 부른 기간 (시작 날, 끝 날)
	public LocalDate[] salesCall;
	public List<HeldPayout> heldPayouts = List.of();
	public List<Hold> holds = List.of();
	public long waitingInquiries;
	public long adminWaitingInquiries;
	public List<LocalDateTime> pendingRequestedAts = List.of();
	// holds를 부른 기간들 (시작 날, 끝 날)
	public final List<LocalDate[]> holdsCalls = new ArrayList<>();
	public boolean unavailable;
	// 문의 상세: 없으면 문의 서비스의 404와 같다. 어느 화면용 상세로 불렸는지 남긴다
	public Inquiry inquiry;
	public AiScreen inquiryScreen;
	public Long inquiryId;

	public void reset() {
		calledMemberIds.clear();
		kitSummary = new KitSummary(0, 0);
		kitPage = new KitPage(List.of(), 0);
		payoutSummary = new PayoutSummary(null, 0, 0);
		salesSummary = new SalesSummary(0, 0, 0, 0);
		salesCall = null;
		heldPayouts = List.of();
		holds = List.of();
		holdsCalls.clear();
		waitingInquiries = 0;
		adminWaitingInquiries = 0;
		pendingRequestedAts = List.of();
		unavailable = false;
		inquiry = null;
		inquiryScreen = null;
		inquiryId = null;
	}

	@Override
	public KitSummary kitSummary(long memberId) {
		called(memberId);
		return kitSummary;
	}

	@Override
	public KitPage preparingKits(long memberId, int size) {
		called(memberId);
		return kitPage;
	}

	@Override
	public SalesSummary salesSummary(long memberId, LocalDate from, LocalDate to) {
		called(memberId);
		salesCall = new LocalDate[] {from, to};
		return salesSummary;
	}

	@Override
	public PayoutSummary payoutSummary(long memberId) {
		called(memberId);
		return payoutSummary;
	}

	@Override
	public List<HeldPayout> heldPayouts(long memberId, YearMonth fromMonth, YearMonth toMonth) {
		called(memberId);
		return heldPayouts;
	}

	@Override
	public List<Hold> holds(long memberId, LocalDate from, LocalDate to) {
		called(memberId);
		holdsCalls.add(new LocalDate[] {from, to});
		// 정산 API처럼 보류한 날이 기간 안인 것만 준다
		return holds.stream()
			.filter(hold -> !hold.heldAt().toLocalDate().isBefore(from) && !hold.heldAt().toLocalDate().isAfter(to))
			.toList();
	}

	@Override
	public long waitingInquiryCount(long memberId, boolean adminAssignedOnly) {
		called(memberId);
		return adminAssignedOnly ? adminWaitingInquiries : waitingInquiries;
	}

	@Override
	public List<LocalDateTime> pendingSellerRequestedAts(long memberId) {
		called(memberId);
		return pendingRequestedAts;
	}

	@Override
	public Optional<Inquiry> inquiry(long memberId, AiScreen screen, long inquiryId) {
		called(memberId);
		inquiryScreen = screen;
		this.inquiryId = inquiryId;
		return Optional.ofNullable(inquiry);
	}

	private void called(long memberId) {
		if (unavailable) {
			throw new IllegalStateException("연결 실패");
		}
		calledMemberIds.add(memberId);
	}
}
