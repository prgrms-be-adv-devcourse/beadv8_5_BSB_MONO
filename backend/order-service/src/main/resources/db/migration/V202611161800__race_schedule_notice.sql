-- 결제자에게 대회 일정·장소 변경을 마지막으로 알린 일정 변경 일시 (BUK-82). 대회마다 한 줄이라 대회 ID를 유니크로 둔다
-- 유니크 제약을 create table 안에 둬야 ddl-auto가 먼저 만든 DB에서도 실패하지 않는다 (FlywayExistingTablesTest)
create table if not exists order_race_schedule_notice (
	id bigint not null auto_increment,
	race_id bigint not null,
	schedule_changed_at datetime(6) not null,
	create_date datetime(6),
	modify_date datetime(6),
	primary key (id),
	constraint uk_race_schedule_notice_race unique (race_id)
) engine=InnoDB;
