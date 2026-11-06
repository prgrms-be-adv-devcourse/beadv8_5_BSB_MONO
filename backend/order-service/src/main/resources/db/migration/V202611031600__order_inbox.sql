-- 받은 Kafka 메시지를 처리했다고 남기는 인박스 (BUK-53). 같은 메시지(그룹 + message-id)가 다시 오면 건너뛴다
create table if not exists order_inbox_kafka_event (
	id bigint not null auto_increment,
	message_id varchar(100) not null,
	consumer_group varchar(100) not null,
	topic varchar(100) not null,
	processed_date datetime(6) not null,
	create_date datetime(6),
	modify_date datetime(6),
	primary key (id),
	constraint uk_order_inbox_group_message unique (consumer_group, message_id)
) engine=InnoDB;
