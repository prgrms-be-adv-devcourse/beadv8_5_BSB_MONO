-- 글·댓글 신고 (BUK-158). 회원 한 명이 대상 하나를 한 번만 신고하도록 (대상 종류, 대상 ID, 신고한 회원) 유니크 제약을 둔다
create table if not exists board_report (
	id bigint not null auto_increment,
	target_type varchar(20) not null,
	target_id bigint not null,
	reporter_id bigint not null,
	reason varchar(20) not null,
	create_date datetime(6),
	modify_date datetime(6),
	primary key (id),
	constraint uk_board_report_target_reporter unique (target_type, target_id, reporter_id)
) engine=InnoDB;

-- 관리자가 되살리거나(RESTORED) 지운(DELETED) 기록. 되살린 뒤에는 이 시각 이후의 신고만 다시 숨김 기준에 센다
alter table board_post add column handled_action varchar(20);
alter table board_post add column handled_at datetime(6);
alter table board_comment add column handled_action varchar(20);
alter table board_comment add column handled_at datetime(6);
