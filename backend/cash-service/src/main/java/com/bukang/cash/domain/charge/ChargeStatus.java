package com.bukang.cash.domain.charge;

public enum ChargeStatus {
	REQUESTED,
	IN_PROGRESS,
	COMPLETED,
	FAILED,
	CANCELED,
	UNCERTAIN // PG 승인 결과를 끝내 확인하지 못함 (재조회 자체가 실패했거나, IN_PROGRESS로 너무 오래 멈춤).
}
