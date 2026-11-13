-- 회수 입금 재확인과 정산 알림에 필요한 칸·ENUM 값을 더한다
-- 정산에 입금 완료를 알린 시각 칸, 회수 입금 상태 EXPIRED(입금 기한 지남)
-- ENUM 목록은 지금 enum 값 전체(Hibernate가 만드는 순서)로 다시 적는다. 이미 있는 값만 남아 있어 기존 행은 그대로다

-- 1. 정산에 알린 시각. ddl-auto: update로 이미 칸을 만든 로컬 DB가 있어, 칸이 없을 때만 더한다
set @sql = (select if(count(*) = 0, 'alter table cash_recovery_deposit add column sent_at datetime(6)', 'select 1')
	from information_schema.columns
	where table_schema = database() and table_name = 'cash_recovery_deposit' and column_name = 'sent_at');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- 2. 회수 입금 상태
alter table cash_recovery_deposit modify status enum('DEPOSITED','EXPIRED','WAITING') not null;
