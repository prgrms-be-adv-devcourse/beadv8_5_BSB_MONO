-- 회수 입금 표: 판매자가 갚을 돈(청구)을 받을 가상계좌와 입금 상태
-- 엔티티는 V1을 쓴 뒤에 dev에 들어와, Hibernate가 MySQL에 만든 DDL을 그대로 옮겼다
-- ddl-auto: update로 이미 표를 만든 서버·로컬 DB가 있어 if not exists로 만든다
create table if not exists cash_recovery_deposit (
	amount bigint not null,
	create_date datetime(6),
	due_date datetime(6) not null,
	id bigint not null auto_increment,
	modify_date datetime(6),
	bank_name varchar(20) not null,
	account_number varchar(40) not null,
	claim_code varchar(64) not null,
	secret varchar(256) not null,
	status enum('WAITING') not null,
	primary key (id),
	constraint UKkvlynj5md4v121e4om1xpkyhh unique (claim_code)
) engine=InnoDB;
