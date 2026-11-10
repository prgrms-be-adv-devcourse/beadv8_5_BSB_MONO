-- 문의 서비스 첫 테이블 (BUK-154). 문의 한 건이 행 하나이고, 답변도 같은 행에 저장한다
-- 인덱스는 create table 안에 둔다 (알림·주문 서비스 V1과 같은 방식). 바꿀 내용은 새 V<날짜시각>__설명.sql로 더한다
create table if not exists inquiry_inquiry (
	id bigint not null auto_increment,
	member_id bigint not null,
	race_id bigint not null,
	type varchar(30) not null,
	assignee varchar(10) not null,
	seller_member_id bigint not null,
	title varchar(100) not null,
	body varchar(2000) not null,
	status varchar(10) not null,
	answer varchar(2000),
	answered_by bigint,
	answered_at datetime(6),
	create_date datetime(6),
	modify_date datetime(6),
	primary key (id),
	index idx_inquiry_member (member_id, id),
	index idx_inquiry_seller (seller_member_id, id),
	index idx_inquiry_status (status, id)
) engine=InnoDB;
