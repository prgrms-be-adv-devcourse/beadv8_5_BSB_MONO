-- 정산 내역 상태에 지급 불일치(PAY_MISMATCH)를 더한다 (BUK-62)
-- 캐시 서비스가 같은 정산 ID를 이미 다른 금액으로 처리했다고 409로 답하면 이 상태로 두고 지급 배치가 다시 고르지 않는다
-- ENUM 목록은 Hibernate가 만드는 순서(알파벳 순)로 적는다. 기존 값을 빼지 않으므로 변환이 실패하지 않는다
alter table payout_payout modify status enum('CALCULATED','CANCELED','HELD','PAID','PAYING','PAY_MISMATCH') not null;
alter table payout_payout_status_history modify from_status enum('CALCULATED','CANCELED','HELD','PAID','PAYING',
	'PAY_MISMATCH');
alter table payout_payout_status_history modify to_status enum('CALCULATED','CANCELED','HELD','PAID','PAYING',
	'PAY_MISMATCH') not null;
