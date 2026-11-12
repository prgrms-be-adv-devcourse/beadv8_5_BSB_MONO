-- 정산이 끝내 처리하지 못해 DLT로 간 Kafka 메시지를 남기는 표 (BUK-166). 칸은 common의 BaseDltKafkaEvent와 같다
-- 상태는 다른 정산 enum 칸과 같이 DB ENUM으로 둔다 (PayoutEnumColumnTest)
-- 같은 메시지는 행 하나로 상태만 바꾸므로 message_id에 유니크를 건다 (NULL은 여러 행 가능)

create table if not exists payout_dlt_kafka_event (
	id bigint not null auto_increment,
	create_date datetime(6),
	modify_date datetime(6),
	original_topic varchar(100) not null,
	dlt_topic varchar(100) not null,
	dlt_partition integer not null,
	dlt_offset bigint not null,
	message_key varchar(100),
	payload text not null,
	message_id varchar(100),
	exception_class varchar(255),
	exception_message varchar(1000),
	status enum('COMPLETED','FAILED','RESENT') not null,
	resent_date datetime(6),
	completed_date datetime(6),
	complete_memo varchar(500),
	primary key (id),
	constraint uk_payout_dlt_message unique (message_id)
) engine=InnoDB;
