package com.bukang.member.out.oauth2;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.bukang.member.app.SocialAccountsUnlinkedEvent;

import lombok.RequiredArgsConstructor;

/**
 * 탈퇴·연결 해제가 커밋된 뒤 제공자 쪽 연결을 끊는다 (BE-034)
 * 롤백되면 부르지 않는다. 연결 끊기가 실패해도 우리 쪽 처리는 이미 끝났으므로 되돌리지 않는다
 */
@Component
@RequiredArgsConstructor
public class SocialUnlinkEventListener {
	private final SocialUnlinkPort socialUnlinkPort;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onUnlinked(SocialAccountsUnlinkedEvent event) {
		event.targets().forEach(target -> socialUnlinkPort.unlink(target.provider(), target.providerUserId()));
	}
}
