-- ddl-auto: update로 만든 서버·로컬 표를 지금 엔티티에 맞춘다
-- V1은 표가 이미 있으면 건너뛰고, update는 ENUM 목록과 NULL 허용을 바꾸지 않는다
-- 그래서 새 enum 값(충전 UNCERTAIN, 환급 IN_PROGRESS, PG 결제취소 원장 종류 등)을 운영에서 저장하면 실패했다
-- 모든 ENUM 칸을 지금 enum 값 전체(Hibernate가 만드는 알파벳 순)로 다시 적는다. 이미 손으로 맞춘 DB에서 돌려도 결과가 같다
-- 값을 더하기만 하므로 이미 있는 행은 그대로 남는다 (목록 밖 값이 있으면 strict 모드라 이 SQL이 실패하고 앱이 뜨지 않는다)
-- 코드의 enum 값을 바꾸면 같은 PR에 ENUM 목록을 바꾸는 SQL을 더한다. 빠뜨리면 CashEnumColumnTest가 실패한다

-- 1. 충전 상태
alter table cash_wallet_charge modify status
	enum('CANCELED','COMPLETED','FAILED','IN_PROGRESS','REQUESTED','UNCERTAIN') not null;

-- 2. 원장 종류·참조 종류
alter table cash_wallet_ledger modify ledger_type enum('사용__주문결제','임시보관__주문결제','임시보관반환__주문취소',
	'정산수령__상품판매_대금','정산수령__상품판매_수수료','정산지급__상품판매_대금','정산지급__상품판매_수수료',
	'출금__PG결제취소_토스페이먼츠','출금__통장입금','출금취소__PG결제취소_토스페이먼츠','출금취소__통장입금',
	'충전__PG결제_토스페이먼츠','환불__주문취소') not null;
alter table cash_wallet_ledger modify ref_type_code enum('Order','Payout','WalletCharge','Withdrawal') not null;

-- 3. 주문 결제 상태·실패 사유
alter table cash_payment modify status enum('CANCELED','COMPLETED','FAILED','PENDING','REFUNDED','TIMED_OUT') not null;
alter table cash_payment modify fail_reason
	enum('DEADLINE_PASSED','INSUFFICIENT_BALANCE','NOT_ON_SALE','OUT_OF_STOCK','PRICE_CHANGED');

-- 4. 환급 상태·실패 사유. 카드 결제취소로만 돌려주는 환급은 은행 칸이 비어 있으므로 NULL을 허용한다
alter table cash_wallet_withdrawal modify status enum('COMPLETED','FAILED','IN_PROGRESS','REQUESTED') not null;
alter table cash_wallet_withdrawal modify fail_reason enum('INVALID_ACCOUNT');
alter table cash_wallet_withdrawal modify bank_code varchar(10);
alter table cash_wallet_withdrawal modify account_number varchar(512);
alter table cash_wallet_withdrawal modify account_holder varchar(50);

-- 5. 환급 몫 상태
alter table cash_wallet_withdrawal_item modify status
	enum('COMPLETED','PENDING','REVERTED','TRANSFERRED_TO_ACCOUNT','UNKNOWN') not null;

-- 6. 정산금 송금 종류
alter table cash_payout_transfer modify transfer_type enum('FEE','SETTLEMENT') not null;
