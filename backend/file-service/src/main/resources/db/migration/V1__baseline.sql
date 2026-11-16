-- 기준선(V1): file-service의 표를 처음 만드는 SQL입니다 (BUK-173)
-- 2026-11-12 dev 기준 엔티티로 Hibernate가 만든 MySQL DDL을 옮겼습니다
-- 모든 서비스가 같은 스키마를 써서, 파일 표가 없는 DB(새 DB)와 이미 있는 DB(ddl-auto: update로 만든 서버·로컬 DB)가 함께 있습니다.
-- 그래서 기준선은 0번으로 두고 이 V1은 항상 실행하되, 표가 이미 있으면 아무것도 하지 않습니다(if not exists)
-- 인덱스·제약은 create table 안에 둡니다. 따로 create index·alter table로 두면 표가 이미 있는 DB에서 "이미 있음" 오류가 납니다
-- Spring Batch 실행 기록 표(BATCH_)는 여기서 만들지 않습니다. 지금처럼 spring.batch.jdbc.initialize-schema가 만듭니다
-- 이 파일은 고치지 않고, 바꿀 내용은 새 V<날짜시각>__설명.sql로 더합니다

-- 처리하지 못한 Kafka 메시지(DLT). status는 Hibernate가 만든 그대로 DB ENUM이라, 상태 값을 더하면 ENUM 목록도 바꿔야 합니다
create table if not exists file_dlt_kafka_event (
	id bigint not null auto_increment,
	create_date datetime(6),
	modify_date datetime(6),
	complete_memo varchar(500),
	completed_date datetime(6),
	dlt_offset bigint not null,
	dlt_partition integer not null,
	dlt_topic varchar(100) not null,
	exception_class varchar(255),
	exception_message varchar(1000),
	message_id varchar(100),
	message_key varchar(100),
	original_topic varchar(100) not null,
	payload TEXT not null,
	resent_date datetime(6),
	status enum ('COMPLETED','FAILED','RESENT') not null,
	primary key (id),
	constraint uk_file_dlt_message unique (message_id)
) engine=InnoDB;

-- 저장한 파일. 대상(ref_type, ref_id)에 붙기 전에는 두 칸이 비어 있습니다
create table if not exists file_file (
	id bigint not null auto_increment,
	create_date datetime(6),
	modify_date datetime(6),
	content_type varchar(100) not null,
	create_user bigint not null,
	file_size bigint not null,
	file_type varchar(20) not null,
	origin_file_nm varchar(255) not null,
	ref_id bigint,
	ref_type varchar(50),
	s3_key varchar(500) not null,
	sort_no integer not null,
	status varchar(20) not null,
	primary key (id),
	constraint uk_file_s3_key unique (s3_key),
	-- 대상별 파일 조회
	index idx_file_ref (ref_type, ref_id, file_type, status),
	-- 정리 배치: 대상에 붙지 않고 오래된 파일 찾기
	index idx_file_status_modify (status, modify_date)
) engine=InnoDB;

-- 받은 Kafka 메시지(인박스). (컨슈머 그룹, 메시지 ID) 유니크 제약으로 같은 메시지를 두 번 처리하지 않습니다
create table if not exists file_inbox_kafka_event (
	id bigint not null auto_increment,
	create_date datetime(6),
	modify_date datetime(6),
	consumer_group varchar(100) not null,
	message_id varchar(100) not null,
	processed_date datetime(6) not null,
	topic varchar(100) not null,
	primary key (id),
	constraint uk_file_inbox_group_message unique (consumer_group, message_id)
) engine=InnoDB;
