-- 환급 카드 몫에 "수동 확인 필요"(MANUAL_CHECK) 상태를 더한다
-- PG가 "이미 취소됨"류로 거절했거나 결과를 모른 채 7일이 지난 몫은 되돌리지 않고 사람이 확인한다
alter table cash_wallet_withdrawal_item modify status
	enum('COMPLETED','MANUAL_CHECK','PENDING','REVERTED','TRANSFERRED_TO_ACCOUNT','UNKNOWN') not null;
