-- 문의 서비스 첫 테이블 (BUK-154). 문의 한 건이 행 하나이고, 답변도 같은 행에 저장한다
-- race_id·order_id·seller_member_id는 비어 있을 수 있다. 관리자 담당 문의는 대회·주문 없이 보낼 수 있고(예치금·가입 문의),
-- 대회만 있고 주문이 없는 문의도 있다. 판매자가 관리자에게 보낸 문의(SELLER_SUPPORT)는 셋 다 비어 있다
-- seller_member_id는 대회 ID가 있을 때만 채운다. 판매자 담당 문의는 대회 ID가 필수라 언제나 채워진다
-- deleted_at이 차 있으면 보낸 회원이 지운 문의다. 행은 남기고 어느 목록·상세에도 보이지 않는다 (3년 보관)
-- 인덱스는 create table 안에 둔다 (알림·주문 서비스 V1과 같은 방식). 바꿀 내용은 새 V<날짜시각>__설명.sql로 더한다
create table if not exists inquiry_inquiry (
	id bigint not null auto_increment,
	member_id bigint not null,
	race_id bigint,
	order_id bigint,
	type varchar(30) not null,
	assignee varchar(10) not null,
	seller_member_id bigint,
	title varchar(100) not null,
	body varchar(1000) not null,
	status varchar(10) not null,
	answer varchar(2000),
	answered_by bigint,
	answered_at datetime(6),
	create_date datetime(6),
	deleted_at datetime(6),
	modify_date datetime(6),
	primary key (id),
	index idx_inquiry_member (member_id, id),
	-- 판매자 목록: 판매자·담당으로 찾고 「답변 대기 먼저, 최신순」(status desc, id desc)을 인덱스 순서로 읽는다
	index idx_inquiry_seller (seller_member_id, assignee, status, id),
	-- 관리자 목록: 전체를 같은 순서로 읽고, 상태로 거르면 (status = ?, id desc)로 읽는다. 상태별 개수도 이 인덱스로 센다
	index idx_inquiry_status (status, id),
	-- 관리자 목록의 담당 필터: 담당으로 찾고 같은 순서로 읽으며, 담당 범위의 상태별 개수도 이 인덱스로 센다
	index idx_inquiry_assignee (assignee, status, id)
) engine=InnoDB;
