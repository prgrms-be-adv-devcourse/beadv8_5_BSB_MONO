-- 주문이 그 대회의 참가권을 모두 환불했다고 알린 시각 칸을 더한다 (BUK-76, PR #467 리뷰)
-- 대회 취소 청구는 이 값이 있을 때만 만든다. 환불이 남아 있을 때 청구하면 늦게 온 환불로 갚을 돈이 청구 금액과 달라진다
-- 기존 행은 환불 완료를 받은 적이 없으므로 null로 둔다

alter table payout_payout
	add column race_refunded_at datetime(6);
