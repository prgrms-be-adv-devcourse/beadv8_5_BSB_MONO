-- 정산금을 은행 송금으로 보내는 데 필요한 칸·ENUM 값을 더한다
-- 정산 지급 기록의 송금 상태·시도 횟수, 원장 종류 정산지급취소__상품판매_대금(송금이 실패해 홀딩 지갑 차감을 되돌림)
-- 기존 행은 판매자 지갑으로 옮겨 이미 끝난 지급이라 완료·1회로 채운다

-- 1. 송금 상태. ddl-auto: update로 이미 칸을 만든 로컬 DB가 있어, 칸이 없을 때만 더한다
set @sql = (select if(count(*) = 0, 'alter table cash_payout_transfer add column status
	enum(''COMPLETED'',''FAILED'',''REQUESTED'',''UNKNOWN'') not null default ''COMPLETED''', 'select 1')
	from information_schema.columns
	where table_schema = database() and table_name = 'cash_payout_transfer' and column_name = 'status');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- 2. 시도 횟수. update가 만든 칸은 기존 행을 0으로 채웠으므로 1로 맞춘다
set @sql = (select if(count(*) = 0, 'alter table cash_payout_transfer add column attempt integer not null default 1',
	'select 1')
	from information_schema.columns
	where table_schema = database() and table_name = 'cash_payout_transfer' and column_name = 'attempt');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
update cash_payout_transfer set attempt = 1 where attempt = 0;

-- 3. 원장 종류
alter table cash_wallet_ledger modify ledger_type enum('사용__주문결제','임시보관__주문결제','임시보관반환__주문취소',
	'정산수령__상품판매_대금','정산수령__상품판매_수수료','정산지급__상품판매_대금','정산지급__상품판매_수수료',
	'정산지급취소__상품판매_대금','출금__PG결제취소_토스페이먼츠','출금__통장입금','출금취소__PG결제취소_토스페이먼츠',
	'출금취소__통장입금','충전__PG결제_토스페이먼츠','환불__주문취소','회수입금__가상계좌_토스페이먼츠') not null;