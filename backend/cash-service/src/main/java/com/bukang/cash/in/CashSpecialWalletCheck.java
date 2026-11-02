package com.bukang.cash.in;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.bukang.cash.config.CashWalletProperties;
import com.bukang.cash.out.WalletRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 앱 시작 시 시스템 지갑·홀딩 지갑이 있는지 확인한다 (WAL-05)
 * 두 지갑은 cash-service가 만들지 않는다. member-service가 회원 ID 1·2번을 시스템·홀딩 회원으로 만들고
 * 가입 이벤트를 보내면, 기존 회원 복제 흐름(CashEventListener)이 그대로 만든다.
 * 아직 만들어지지 않았으면(member-service가 이벤트를 안 보냈거나 시딩 전이면) 경고만 남기고 넘어간다.
 * 테스트 프로파일에서는 실행하지 않는다
 */
@Slf4j
@Profile("!test")
@Component
@RequiredArgsConstructor
public class CashSpecialWalletCheck implements ApplicationRunner {
	private final CashWalletProperties cashWalletProperties;
	private final WalletRepository walletRepository;

	@Override
	public void run(ApplicationArguments args) {
		checkWallet(cashWalletProperties.systemMemberId());
		checkWallet(cashWalletProperties.holdingMemberId());
	}

	private void checkWallet(long memberId) {
		if (walletRepository.findByHolderId(memberId).isPresent()) {
			return;
		}
		log.warn(
			"지갑(memberId={})이 없습니다. member-service가 이 ID로 회원을 만들고 가입 이벤트를 보내야 합니다.", memberId
		);
	}
}
