package com.bukang.cash.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 충전 대사 배치 설정. staleInProgressMinutes는 충전 확정 한 번이 걸릴 수 있는 가장 긴 시간(선점 대기 예산)보다
// 충분히 길어야, 아직 처리 중인 정상 요청을 멈춘 것으로 잘못 보지 않는다
@ConfigurationProperties("cash.charge.reconcile")
public record ChargeReconcileProperties(
	long staleInProgressMinutes,
	long alertAfterMinutes
) {
}
