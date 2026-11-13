-- 회수 입금 알림(가상계좌 입금 완료)을 받아 기록하는 데 필요한 칸·ENUM 값을 더한다
-- 입금 시각 칸, 회수 입금 상태 DEPOSITED, 원장 참조 종류 RecoveryDeposit, 원장 종류 회수입금__가상계좌_토스페이먼츠
-- ENUM 목록은 지금 enum 값 전체(Hibernate가 만드는 순서)로 다시 적는다. 이미 있는 값만 남아 있어 기존 행은 그대로다

-- 1. 입금 시각. ddl-auto: update로 이미 칸을 만든 로컬 DB가 있어, 칸이 없을 때만 더한다
set @sql = (select if(count(*) = 0, 'alter table cash_recovery_deposit add column deposited_at datetime(6)', 'select 1')
	from information_schema.columns
	where table_schema = database() and table_name = 'cash_recovery_deposit' and column_name = 'deposited_at');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- 2. 회수 입금 상태
alter table cash_recovery_deposit modify status enum('DEPOSITED','WAITING') not null;

-- 3. 원장 참조 종류·원장 종류
alter table cash_wallet_ledger modify ref_type_code
	enum('Order','Payout','RecoveryDeposit','WalletCharge','Withdrawal') not null;
alter table cash_wallet_ledger modify ledger_type enum('사용__주문결제','임시보관__주문결제','임시보관반환__주문취소',
	'정산수령__상품판매_대금','정산수령__상품판매_수수료','정산지급__상품판매_대금','정산지급__상품판매_수수료',
	'출금__PG결제취소_토스페이먼츠','출금__통장입금','출금취소__PG결제취소_토스페이먼츠','출금취소__통장입금',
	'충전__PG결제_토스페이먼츠','환불__주문취소','회수입금__가상계좌_토스페이먼츠') not null;
