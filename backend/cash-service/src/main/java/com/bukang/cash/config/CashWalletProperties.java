package com.bukang.cash.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 플랫폼이 직접 보관하는 특수 지갑의 회원 ID (cash.wallet.*, application.yaml에서 값을 정한다)
 * 시스템 지갑(플랫폼 수수료 수익), 홀딩 지갑(판매자 정산 전 보관)이다 (docs/erd/cash.md 홀딩 지갑).
 * 두 회원은 member-service가 만든다. cash-service는 ID만 알고, 지갑이 있는지 확인만 한다 (CashSpecialWalletCheck).
 */
@ConfigurationProperties("cash.wallet")
public record CashWalletProperties(
	long systemMemberId,
	long holdingMemberId
) {
}
