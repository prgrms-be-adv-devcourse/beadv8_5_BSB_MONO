-- 대회 AI 추천의 동기화 표와 칸 (BUK-162)
-- Flyway가 한 번만 실행하므로 V1과 달리 if not exists 없이 만든다

-- 문서 동기화 이벤트: 처리 실패가 된 시각(실패·완료 알림 판단)과 사람이 직접 닫을 때 남기는 메모
-- 테스트 DB(H2)가 한 문장에 여러 칸 추가를 받지 않아 칸마다 나눈다
alter table product_race_document_sync_event add column failed_date datetime(6);
alter table product_race_document_sync_event add column complete_memo varchar(500);

-- 임베딩 동기화 이벤트: 대회의 AI 추천 벡터를 만들 일. 칸은 문서 동기화 이벤트와 같다 (메모 칸만 없다)
create table product_race_embedding_sync_event (
	id bigint not null auto_increment,
	create_date datetime(6),
	modify_date datetime(6),
	complete_date datetime(6),
	failed_date datetime(6),
	next_retry_date datetime(6),
	race_id bigint not null,
	retry_count integer not null,
	status varchar(20) not null,
	primary key (id),
	index idx_race_embedding_sync_status_create (status, create_date)
) engine=InnoDB;
