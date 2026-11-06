-- 알림 서비스 첫 표 (BUK-69). 받은 알림 요청 하나가 행 하나다
-- message_id 유니크가 인박스 역할을 한다: 같은 메시지를 두 번 받으면 두 번째 저장이 실패한다
-- 인덱스·제약은 create table 안에 둔다 (주문 서비스 V1과 같은 방식). 바꿀 내용은 새 V<날짜시각>__설명.sql로 더한다
create table if not exists notification_notification (
	id bigint not null auto_increment,
	message_id varchar(36) not null,
	member_id bigint not null,
	kind varchar(40) not null,
	title varchar(100) not null,
	body varchar(500) not null,
	href varchar(300) not null,
	occurred_at datetime(6) not null,
	read_at datetime(6),
	create_date datetime(6),
	modify_date datetime(6),
	primary key (id),
	constraint uk_notification_message unique (message_id),
	index idx_notification_member_occurred (member_id, occurred_at)
) engine=InnoDB;
