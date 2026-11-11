package com.bukang.cash.app.withdrawal;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.cash.app.payment.CashRefundableChargeQueryUseCase;
import com.bukang.cash.dto.payment.RefundableChargeDto;
import com.bukang.cash.out.charge.WalletChargeRepository;
import com.bukang.common.global.eventpublisher.EventPublisher;
import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;

import lombok.RequiredArgsConstructor;

/**
 * PG 결제취소 기한 3일 전·1일 전 알림
 * 정책 5장 2차 1 「PG 결제취소와 기한 › 기한 알림」: 회원·마지막 날별로 남은 금액을 합쳐 알림 한 건을 보낸다
 */
@Service
@RequiredArgsConstructor
public class CashCardCancelDeadlineNoticeUseCase {
	private static final List<Integer> DAYS_BEFORE = List.of(3, 1);
	private static final String HREF = "/me/deposit/refund";

	private final WalletChargeRepository walletChargeRepository;
	private final CashRefundableChargeQueryUseCase cashRefundableChargeQueryUseCase;
	private final EventPublisher eventPublisher;

	// now는 한국 시각이다. 마지막 날이 딱 3일 뒤·1일 뒤인 몫만 보고, 배치가 못 돈 날의 알림은 다음 날 보내지 않는다
	@Transactional(readOnly = true)
	public void notifyDeadlines(LocalDateTime now) {
		for (int daysBefore : DAYS_BEFORE) {
			LocalDate deadline = now.toLocalDate().plusDays(daysBefore);
			for (long memberId : findMemberIds(deadline)) {
				long amount = remainingAmountDue(memberId, deadline, now);
				if (amount > 0) {
					eventPublisher.publish(notice(memberId, deadline, daysBefore, amount, now));
				}
			}
		}
	}

	// 마지막 날(승인일 + 1년 − 1일)을 거꾸로 풀면 윤년에서 어긋나므로(2/29 승인 → 2/27) 승인일을 넉넉히 찾고,
	// 마지막 날이 같은지는 remainingAmountDue가 다시 본다
	private List<Long> findMemberIds(LocalDate deadline) {
		return walletChargeRepository.findWalletIdsWithCompletedChargeApprovedBetween(
			deadline.minusYears(1).atStartOfDay(), deadline.plusDays(3).minusYears(1).atStartOfDay());
	}

	// 남은 금액은 환급 화면과 같은 규칙(최근 충전부터 채움)으로 보내는 날 다시 계산한다
	private long remainingAmountDue(long memberId, LocalDate deadline, LocalDateTime now) {
		return cashRefundableChargeQueryUseCase.findRefundableCharges(memberId, now).charges().stream()
			.filter(charge -> charge.lastCancelableDate().equals(deadline))
			.mapToLong(RefundableChargeDto::remainingAmount)
			.sum();
	}

	private static NotificationRequestedEvent notice(long memberId, LocalDate deadline, int daysBefore, long amount,
		LocalDateTime now) {
		String title = daysBefore == 1
			? "카드로 돌려받을 수 있는 기한이 내일까지예요"
			: "카드로 돌려받을 수 있는 기한이 " + daysBefore + "일 남았어요";
		String body = String.format(Locale.KOREA,
			"%s까지 환급하면 %,d원을 카드로 돌려받아요.\n기한이 지나면 계좌로만 돌려받을 수 있어요.", deadline, amount);
		// 배치가 다시 돌아도 알림 서비스가 한 번만 저장하도록(message_id 유니크) 메시지 ID를 회원·마지막 날·며칠 전으로 정한다
		String key = "card-cancel-deadline:" + memberId + ":" + deadline + ":" + daysBefore;
		String eventId = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
		return new NotificationRequestedEvent(eventId, memberId, NotificationKind.CARD_CANCEL_DEADLINE, title, body,
			HREF, now);
	}
}
