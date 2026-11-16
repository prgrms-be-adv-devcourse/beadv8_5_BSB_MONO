-- 판매자가 참가자 명단·키트 배송 목록을 내려받은 기록 (BUK-228). 누가(회원·판매자) 언제 무슨 목록을 왜 받았는지 남긴다
-- 인덱스를 create table 안에 둬야 ddl-auto가 먼저 만든 DB에서도 실패하지 않는다 (FlywayExistingTablesTest)
create table if not exists order_seller_export_log (
	id bigint not null auto_increment,
	seller_id bigint not null,
	member_id bigint not null,
	race_id bigint,
	export_type varchar(20) not null,
	reason varchar(200) not null,
	row_count integer not null,
	create_date datetime(6),
	modify_date datetime(6),
	primary key (id),
	index idx_seller_export_log_seller (seller_id, id)
) engine=InnoDB;
