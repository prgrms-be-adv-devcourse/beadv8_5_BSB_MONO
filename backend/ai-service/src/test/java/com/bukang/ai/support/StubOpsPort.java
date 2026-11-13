package com.bukang.ai.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.bukang.ai.out.ops.OpsPort;

// 주문·정산·문의·회원 서비스 대역. 정해 둔 값을 돌려주고, 어느 회원 번호로 불렸는지 남긴다
public class StubOpsPort implements OpsPort {
	public final List<Long> calledMemberIds = new ArrayList<>();
	public KitSummary kitSummary = new KitSummary(0, 0);
	public KitPage kitPage = new KitPage(List.of(), 0);
	public PayoutSummary payoutSummary = new PayoutSummary(null, 0, 0);
	public List<Hold> holds = List.of();
	public long waitingInquiries;
	public long adminWaitingInquiries;
	public List<LocalDateTime> pendingRequestedAts = List.of();
	public LocalDate holdsFrom;
	public LocalDate holdsTo;
	public boolean unavailable;

	public void reset() {
		calledMemberIds.clear();
		kitSummary = new KitSummary(0, 0);
		kitPage = new KitPage(List.of(), 0);
		payoutSummary = new PayoutSummary(null, 0, 0);
		holds = List.of();
		waitingInquiries = 0;
		adminWaitingInquiries = 0;
		pendingRequestedAts = List.of();
		unavailable = false;
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
	public PayoutSummary payoutSummary(long memberId) {
		called(memberId);
		return payoutSummary;
	}

	@Override
	public List<Hold> holds(long memberId, LocalDate from, LocalDate to) {
		called(memberId);
		holdsFrom = from;
		holdsTo = to;
		return holds;
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

	private void called(long memberId) {
		if (unavailable) {
			throw new IllegalStateException("연결 실패");
		}
		calledMemberIds.add(memberId);
	}
}
