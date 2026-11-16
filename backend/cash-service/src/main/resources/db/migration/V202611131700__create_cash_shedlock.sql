-- 스케줄 작업 잠금 표 (BUK-206). pod가 여러 개여도 같은 작업은 이 표에 먼저 이름을 적은 한 곳에서만 돈다
-- 칸은 ShedLock JdbcTemplateLockProvider가 정한 그대로다 (정산 payout_shedlock과 같다)
create table if not exists cash_shedlock (
	name varchar(64) not null,
	lock_until datetime(6) not null,
	locked_at datetime(6) not null,
	locked_by varchar(255) not null,
	primary key (name)
) engine=InnoDB;
