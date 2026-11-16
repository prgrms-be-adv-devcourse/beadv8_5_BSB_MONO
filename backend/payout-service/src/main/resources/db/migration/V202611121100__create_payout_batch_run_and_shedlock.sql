-- 정산 배치 실행 기록과 배치 잠금 표 (BUK-167)
-- 실행 기록의 enum 칸은 다른 정산 enum 칸과 같이 DB ENUM이다 (PayoutEnumColumnTest). 목록은 유니코드 순

create table if not exists payout_batch_run (
	id bigint not null auto_increment,
	create_date datetime(6),
	modify_date datetime(6),
	batch_type enum('생성','지급','청구') not null,
	trigger_type enum('관리자','스케줄') not null,
	run_date date not null,
	started_by bigint,
	started_at datetime(6) not null,
	finished_at datetime(6),
	status enum('실패','완료','일부_실패','진행_중') not null,
	done_count integer not null,
	problem_count integer not null,
	summary varchar(255),
	primary key (id),
	index idx_payout_batch_run_run_date (run_date)
) engine=InnoDB;

-- ShedLock 잠금 표. 칸은 ShedLock JdbcTemplateLockProvider가 정한 그대로다
create table if not exists payout_shedlock (
	name varchar(64) not null,
	lock_until datetime(6) not null,
	locked_at datetime(6) not null,
	locked_by varchar(255) not null,
	primary key (name)
) engine=InnoDB;
