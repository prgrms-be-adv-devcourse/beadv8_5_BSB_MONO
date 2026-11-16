-- 끝내 처리하지 못해 DLT(<토픽>.order.dlt)로 간 Kafka 메시지 (BUK-180). 칸은 공통 BaseDltKafkaEvent와 같다
-- 유니크 제약을 create table 안에 둬야 ddl-auto가 먼저 만든 DB에서도 실패하지 않는다 (FlywayExistingTablesTest)
create table if not exists order_dlt_kafka_event (
	id bigint not null auto_increment,
	original_topic varchar(100) not null,
	dlt_topic varchar(100) not null,
	dlt_partition integer not null,
	dlt_offset bigint not null,
	message_key varchar(100),
	payload text not null,
	message_id varchar(100),
	exception_class varchar(255),
	exception_message varchar(1000),
	status varchar(20) not null,
	resent_date datetime(6),
	completed_date datetime(6),
	complete_memo varchar(500),
	create_date datetime(6),
	modify_date datetime(6),
	primary key (id),
	constraint uk_order_dlt_message unique (message_id)
) engine=InnoDB;
