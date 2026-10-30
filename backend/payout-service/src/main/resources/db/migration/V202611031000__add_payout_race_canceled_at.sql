-- 지급 중(PAYING)에 대회가 취소된 시각 칸을 더한다 (PRO-253)
-- 지급 중에는 돈이 이미 나갔을 수 있어 바로 닫지 못하므로 표시만 남기고, 지급 결과가 나오면 취소 · 회수로 마무리한다
-- 기존 행은 대회 취소를 표시한 적이 없으므로 null로 둔다

alter table payout_payout
	add column race_canceled_at datetime(6);
