package com.bukang.payout.app;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.bukang.common.global.notify.SlackNotifier;
import com.bukang.common.shared.notification.NotificationKafkaSender;
import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.payout.domain.PayoutClaim;
import com.bukang.payout.out.RaceSellerPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 청구 알림을 보낸다 (CRU-94). 판매자에게는 내 알림(PAYOUT_CLAIM_REQUESTED), 관리자에게는 Slack
 * PayoutClaimUseCase.run이 청구 트랜잭션을 커밋한 뒤에 부른다. 상품 서비스 조회(최대 3초)와 Slack 호출 동안 청구 행을 잠그지 않고,
 * 여기서 실패해도 청구 저장은 되돌아가지 않는다. 알림은 잃어도 되는 메시지라(11/04 결정) 예외를 밖으로 던지지 않는다
 * 판매자 알림은 입금 확인 전까지 매일 나가므로 하루를 놓쳐도 다음 날 다시 간다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutClaimNotifier {
	private static final String TITLE = "정산금 반환을 요청드려요";
	private static final String HREF = "/seller/settlements";
	private static final DateTimeFormatter DUE_DATE = DateTimeFormatter.ofPattern("yyyy.M.d");

	private final RaceSellerPort raceSellerPort;
	private final NotificationKafkaSender notificationKafkaSender;
	private final SlackNotifier slackNotifier;

	/**
	 * @param raceName 그 대회의 정산 내역에 적힌 대회 이름. 없으면 null
	 */
	public void notifySeller(PayoutClaim claim, String raceName) {
		try {
			Optional<Long> sellerMemberId = raceSellerPort.findSellerMemberId(claim.getRaceId());
			if (sellerMemberId.isEmpty()) {
				log.warn("대회가 없어 청구 알림을 보내지 않았습니다. claimCode={}, raceId={}", claim.getClaimCode(),
					claim.getRaceId());
				return;
			}
			notificationKafkaSender.send(NotificationRequestedEvent.of(sellerMemberId.get(),
				NotificationKind.PAYOUT_CLAIM_REQUESTED, TITLE, body(claim, raceName), HREF));
		} catch (RuntimeException exception) {
			log.warn("청구 알림을 보내지 못했습니다. claimCode={}, raceId={}, cause={}", claim.getClaimCode(),
				claim.getRaceId(), exception.toString());
		}
	}

	// 계좌번호는 넣지 않는다 (CRU-81). 관리자 화면의 청구 목록에서 청구 ID로 찾는다
	public void notifyAdmin(PayoutClaim claim) {
		try {
			slackNotifier.send("[정산] 청구하고 30일이 지나도 판매자가 돌려주지 않았습니다. "
				+ "claimCode=%s, sellerId=%d, raceId=%d, amount=%,d원, 상계 실패일=%s".formatted(claim.getClaimCode(),
				claim.getSellerId(), claim.getRaceId(), claim.getAmount(), claim.getStartDate()));
		} catch (RuntimeException exception) {
			log.warn("관리자 청구 알림을 보내지 못했습니다. claimCode={}, cause={}", claim.getClaimCode(),
				exception.toString());
		}
	}

	private static String body(PayoutClaim claim, String raceName) {
		String race = raceName == null ? "판매하신 대회" : raceName;
		// 가상계좌 발급 전이면 계좌 없이 보낸다. 발급된 다음 날 알림에 계좌가 실린다
		String account = claim.getVirtualAccountNumber() == null
			? "발급되는 대로 다시 알려 드려요"
			: claim.getVirtualBankName() + " " + claim.getVirtualAccountNumber();
		return "%s 정산에서 환불로 돌려받아야 할 금액이 남았어요.\n청구 금액: %,d원\n입금 계좌: %s\n입금 기한: %s"
			.formatted(race, claim.getAmount(), account, claim.getDueDate().format(DUE_DATE));
	}
}
