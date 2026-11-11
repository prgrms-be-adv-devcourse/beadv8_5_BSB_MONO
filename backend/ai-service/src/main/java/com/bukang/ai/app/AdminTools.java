package com.bukang.ai.app;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.bukang.ai.domain.AiSource;
import com.bukang.ai.out.ops.OpsPort;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 화면의 도구. 관리자 화면 질문에만 붙는다 (AiChatUseCase)
 * 건수와 날짜만 돌려준다. 회원 이름·연락처 같은 개인정보는 담지 않는다 (가린 값을 주는 API가 아직 없다)
 */
@Component
@RequiredArgsConstructor
public class AdminTools {
	private final OpsPort opsPort;

	@Tool(description = "마지막 질문이 답변 대기 문의 수를 물을 때만 부른다. waitingCount: 전체 답변 대기 문의 수, "
		+ "adminAssignedWaitingCount: 그중 관리자가 답해야 하는(관리자 담당) 문의 수")
	public Object adminWaitingInquiries(ToolContext toolContext) {
		AiToolTrace trace = AiToolTrace.of(toolContext);
		return trace.run(AiSource.ADMIN_INQUIRIES, () -> new InquiriesResult(
			opsPort.waitingInquiryCount(trace.memberId(), false),
			opsPort.waitingInquiryCount(trace.memberId(), true)));
	}

	@Tool(description = "마지막 질문이 승인 대기 판매자를 물을 때만 부른다. pendingCount: 승인 대기 건수, "
		+ "oldestRequestedDate: 가장 오래된 신청일(없으면 null). 신청자 이름·연락처는 조회할 수 없으니 화면에서 확인하라고 안내한다")
	public Object adminPendingSellers(ToolContext toolContext) {
		AiToolTrace trace = AiToolTrace.of(toolContext);
		return trace.run(AiSource.ADMIN_SELLERS, () -> {
			List<LocalDateTime> requestedAts = opsPort.pendingSellerRequestedAts(trace.memberId());
			LocalDate oldest = requestedAts.stream().min(LocalDateTime::compareTo)
				.map(LocalDateTime::toLocalDate).orElse(null);
			return new PendingSellersResult(requestedAts.size(), oldest);
		});
	}

	record InquiriesResult(long waitingCount, long adminAssignedWaitingCount) {
	}

	record PendingSellersResult(int pendingCount, LocalDate oldestRequestedDate) {
	}
}
