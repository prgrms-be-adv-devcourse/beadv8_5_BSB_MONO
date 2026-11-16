package com.bukang.ai.app;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.bukang.ai.domain.AiScreen;
import com.bukang.ai.domain.exception.AiForbiddenException;
import com.bukang.ai.dto.AiBriefingDto;
import com.bukang.ai.dto.AiBriefingDto.Metric;
import com.bukang.ai.dto.AiBriefingDto.Todo;
import com.bukang.ai.out.member.MemberRolePort;
import com.bukang.ai.out.ops.OpsPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 대시보드 브리핑. 모델을 부르지 않고 조회한 숫자로 정해진 문장을 만든다 (대시보드를 열 때마다 빨라야 하고 비용이 없어야 한다)
 * 조회 하나가 실패해도 카드 전체를 깨뜨리지 않는다. 그 칸만 「불러오지 못했어요」로 바꾼다
 * 문구·링크 글은 추천값(미확정)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiBriefingUseCase {
	static final String FAILED = "지금 불러오지 못했어요. 화면에서 확인해 주세요.";
	private static final DateTimeFormatter BASIS = DateTimeFormatter.ofPattern("a h:mm 기준", Locale.KOREAN);
	private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M/d", Locale.KOREAN);

	private final MemberRolePort memberRolePort;
	private final OpsPort opsPort;

	public AiBriefingDto briefing(long memberId, AiScreen screen) {
		checkRole(memberId, screen);
		LocalDateTime now = LocalDateTime.now(AiToolTrace.ZONE);
		return switch (screen) {
			case SELLER -> seller(memberId, now);
			case ADMIN -> admin(memberId, now);
		};
	}

	private AiBriefingDto seller(long memberId, LocalDateTime now) {
		OpsPort.KitSummary kits = fetch("kits", memberId, () -> opsPort.kitSummary(memberId));
		OpsPort.PayoutSummary payout = fetch("payout", memberId, () -> opsPort.payoutSummary(memberId));
		List<String> sentences = new ArrayList<>();
		List<Todo> todos = new ArrayList<>();
		List<Metric> metrics = new ArrayList<>();

		if (kits == null) {
			todos.add(new Todo("송장 입력 대기", FAILED, "키트 배송 관리", "/seller/kits"));
			metrics.add(new Metric("보낼 키트", "-", "불러오지 못했어요", null));
		} else {
			// 배송 준비 중 환불 기한 전 건은 아직 보낼 수 없다 (SellerTools와 같은 계산)
			long sendable = Math.max(0, kits.preparing() - kits.beforeRefundDeadline());
			long later = kits.beforeRefundDeadline();
			if (sendable > 0) {
				sentences.add("송장 입력 대기 키트가 %d건 있어요.".formatted(sendable));
				todos.add(new Todo("송장 입력 대기 %d건".formatted(sendable),
					later > 0 ? "환불 기한이 지나면 %d건을 더 보낼 수 있어요".formatted(later) : "송장 번호를 넣으면 배송 중으로 바뀌어요",
					"키트 배송 관리", "/seller/kits"));
			} else if (later > 0) {
				sentences.add("환불 기한이 지나면 보낼 키트가 %d건 있어요.".formatted(later));
				todos.add(new Todo("환불 기한 뒤 보낼 키트 %d건".formatted(later), "지금 보낼 키트는 없어요. 환불 기한이 지나면 송장을 넣을 수 있어요",
					"키트 배송 관리", "/seller/kits"));
			}
			metrics.add(new Metric("보낼 키트", "%d건".formatted(sendable), "환불 기한 전 %d건".formatted(later), null));
		}

		if (payout == null) {
			todos.add(new Todo("지급보류", FAILED, "지급보류 내역", "/seller/settlements?tab=holds"));
			metrics.add(new Metric("다음 정산", "-", "불러오지 못했어요", null));
		} else {
			if (payout.heldCount() > 0) {
				sentences.add("지급보류 %d건의 사유를 확인해 주세요.".formatted(payout.heldCount()));
				todos.add(new Todo("지급보류 %d건".formatted(payout.heldCount()), "보류가 풀려야 그 정산이 지급돼요",
					"지급보류 내역", "/seller/settlements?tab=holds"));
			}
			metrics.add(payout.nextPayDate() == null
				? new Metric("다음 정산", "-", "지급 예정 정산이 없어요", null)
				: new Metric("다음 정산 · %s 지급".formatted(payout.nextPayDate().format(MONTH_DAY)),
					won(payout.scheduledPayoutAmount()),
					payout.heldCount() > 0 ? "보류 %d건 빼고".formatted(payout.heldCount()) : "보류 없음", null));
		}
		return new AiBriefingDto(headline(sentences, kits == null || payout == null), now.format(BASIS),
			todos, metrics);
	}

	// shortcut: 조회를 차례로 부른다(조회마다 시간 초과 ai.clients.timeout 2초). 카드가 느려지면 병렬로 바꾼다
	private AiBriefingDto admin(long memberId, LocalDateTime now) {
		List<LocalDateTime> requestedAts =
			fetch("sellers", memberId, () -> opsPort.pendingSellerRequestedAts(memberId));
		// 두 숫자 모두 같은 문의 API라 함께 성공하거나 함께 실패한다
		long[] inquiries = fetch("inquiries", memberId, () -> new long[] {
			opsPort.waitingInquiryCount(memberId, false), opsPort.waitingInquiryCount(memberId, true)});
		List<String> sentences = new ArrayList<>();
		List<Todo> todos = new ArrayList<>();
		List<Metric> metrics = new ArrayList<>();

		if (requestedAts == null) {
			todos.add(new Todo("판매자 승인 대기", FAILED, "판매자 승인", "/admin/sellers"));
			metrics.add(new Metric("판매자 승인 대기", "-", "불러오지 못했어요", null));
		} else {
			int pending = requestedAts.size();
			LocalDate oldest = requestedAts.stream().filter(Objects::nonNull).min(LocalDateTime::compareTo)
				.map(LocalDateTime::toLocalDate).orElse(null);
			// 신청한 날이 1일째. 신청 시각이 서버 오늘보다 뒤여도(시계 차이) 1일째로 둔다
			String waited = oldest == null ? (pending > 0 ? "신청일 확인 필요" : "대기 없음") : "가장 오래된 신청 %d일째"
				.formatted(Math.max(1, ChronoUnit.DAYS.between(oldest, now.toLocalDate()) + 1));
			if (pending > 0) {
				sentences.add("판매자 승인 대기가 %d건 있어요.".formatted(pending));
				todos.add(new Todo("판매자 승인 대기 %d건".formatted(pending),
					oldest == null ? "신청 목록을 확인해 주세요" : "%s 신청부터 확인해 주세요".formatted(oldest.format(MONTH_DAY)),
					"판매자 승인", "/admin/sellers"));
			}
			metrics.add(new Metric("판매자 승인 대기", "%d건".formatted(pending), waited, null));
		}

		if (inquiries == null) {
			todos.add(new Todo("답변 대기 문의", FAILED, "문의 관리", "/admin/inquiries"));
			metrics.add(new Metric("답변 대기 문의 · 전체", "-", "불러오지 못했어요", null));
		} else {
			long all = inquiries[0];
			long mine = inquiries[1];
			if (mine > 0) {
				sentences.add("관리자가 답할 문의가 %d건 있어요.".formatted(mine));
				todos.add(new Todo("답변 대기 문의 · 관리자 담당 %d건".formatted(mine),
					"판매자 담당까지 전체 %d건이 답변을 기다려요".formatted(all), "문의 관리", "/admin/inquiries"));
			}
			metrics.add(new Metric("답변 대기 문의 · 전체", "%d건".formatted(all), "관리자 담당 %d건".formatted(mine), null));
		}
		return new AiBriefingDto(headline(sentences, requestedAts == null || inquiries == null), now.format(BASIS),
			todos, metrics);
	}

	private static String headline(List<String> sentences, boolean anyFailed) {
		if (sentences.isEmpty()) {
			return anyFailed ? "일부 숫자를 불러오지 못했어요. 아래 화면에서 확인해 주세요." : "오늘 먼저 처리할 일이 없어요.";
		}
		// 실패한 칸이 있으면 headline만 보고 「그것뿐」이라고 읽지 않게 알린다
		return String.join(" ", sentences) + (anyFailed ? " 일부 숫자는 불러오지 못했어요." : "");
	}

	private static String won(long amount) {
		return "₩" + NumberFormat.getIntegerInstance(Locale.KOREA).format(amount);
	}

	// 실패하면 null. 그 칸만 비운다
	private <T> T fetch(String what, long memberId, Supplier<T> query) {
		try {
			return query.get();
		} catch (RuntimeException exception) {
			log.warn("브리핑 숫자를 가져오지 못했습니다. what={}, memberId={}", what, memberId, exception);
			return null;
		}
	}

	// chat API와 같은 역할 확인 (AiChatUseCase.checkRole)
	private void checkRole(long memberId, AiScreen screen) {
		boolean allowed = switch (screen) {
			case SELLER -> memberRolePort.isApprovedSeller(memberId);
			case ADMIN -> memberRolePort.isAdmin(memberId);
		};
		if (!allowed) {
			throw new AiForbiddenException(screen);
		}
	}
}
