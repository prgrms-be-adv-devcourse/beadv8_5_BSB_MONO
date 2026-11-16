-- 글 색인 아웃박스 (BUK-179). 글을 저장하는 트랜잭션 안에서 행을 남기고, 릴레이가 1초마다 Kafka(queuing.post.changed)로 보낸다
-- product·order 아웃박스와 같은 모양. message_id는 행을 만들 때 한 번 정해, 다시 보내도 같은 값이 나간다 (받는 쪽 인박스가 거른다)
create table if not exists board_outbox_kafka_event (
	id bigint not null auto_increment,
	message_id varchar(100) not null,
	ref_type varchar(50) not null,
	ref_id bigint not null,
	event_type varchar(100) not null,
	payload text not null,
	status varchar(20) not null,
	retry_count int not null,
	next_retry_date datetime(6),
	sent_date datetime(6),
	create_date datetime(6),
	modify_date datetime(6),
	primary key (id),
	-- 미발행(PENDING) 행을 만든 순서대로 빠르게 찾는다 (릴레이가 쓴다)
	index idx_board_outbox_kafka_status_create (status, create_date),
	constraint uk_board_outbox_kafka_message_id unique (message_id)
) engine=InnoDB;
