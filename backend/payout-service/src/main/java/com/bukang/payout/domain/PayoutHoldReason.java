package com.bukang.payout.domain;

/**
 * 지급보류 사유 종류 (docs/policy/payout.md 1장 지급보류 2번)
 * 판매자 화면에 사유를 보여 주고, 자동 보류(계좌 미등록)를 사유 종류로 찾기 위해 enum으로 둔다
 */
public enum PayoutHoldReason {
	정산_계좌_미등록,
	대회_취소_처리;
}
