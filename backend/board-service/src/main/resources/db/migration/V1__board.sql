-- 게시판 서비스 첫 표 (BUK-156). 글, 댓글, 좋아요, 회원 복사본, 받은 Kafka 메시지(인박스)
-- 인덱스·제약은 create table 안에 둔다 (주문 서비스 V1과 같은 방식). 바꿀 내용은 새 V<날짜시각>__설명.sql로 더한다
-- 신고 표(board_report)와 검색 색인 아웃박스(board_outbox_kafka_event)는 다음 이슈(BUK-157·158)에서 더한다

-- 글. status는 VISIBLE(보임)·HIDDEN(신고로 숨김)·DELETED(지움). 좋아요 수·신고 수는 숫자로 같이 저장한다 (인기순 정렬마다 세지 않으려고)
-- 신고 수·숨김 상태는 신고 기능(BUK-158)이 쓰지만 표는 지금 만든다
create table if not exists board_post (
	id bigint not null auto_increment,
	member_id bigint not null,
	category varchar(20) not null,
	title varchar(50) not null,
	content varchar(5000) not null,
	status varchar(20) not null,
	edited bit not null,
	like_count bigint not null,
	report_count int not null,
	create_date datetime(6),
	modify_date datetime(6),
	primary key (id),
	-- 목록 커서: 최신순 (상태, 작성 시각, 글 ID), 인기순 (상태, 좋아요 수, 글 ID)
	index idx_board_post_latest (status, create_date, id),
	index idx_board_post_popular (status, like_count, id)
) engine=InnoDB;

-- 댓글·답글. parent_id가 비어 있으면 댓글, 있으면 그 댓글의 답글이다 (답글의 답글은 없다)
create table if not exists board_comment (
	id bigint not null auto_increment,
	post_id bigint not null,
	parent_id bigint,
	member_id bigint not null,
	content varchar(500) not null,
	status varchar(20) not null,
	edited bit not null,
	create_date datetime(6),
	modify_date datetime(6),
	primary key (id),
	index idx_board_comment_post (post_id, id)
) engine=InnoDB;

-- 좋아요. 회원당 글 하나에 한 번: (글, 회원) 유니크 제약이 동시에 온 두 요청 중 하나만 들어가게 한다
create table if not exists board_post_like (
	id bigint not null auto_increment,
	post_id bigint not null,
	member_id bigint not null,
	create_date datetime(6),
	modify_date datetime(6),
	primary key (id),
	constraint uk_board_like_post_member unique (post_id, member_id)
) engine=InnoDB;

-- 회원 복사본. id는 member-service의 회원 ID를 그대로 쓴다 (회원 가입·수정·탈퇴, 판매자 변경 이벤트로 채운다)
create table if not exists board_member (
	id bigint not null,
	nickname varchar(100),
	seller_approved bit not null,
	withdrawn bit not null,
	create_date datetime(6),
	modify_date datetime(6),
	primary key (id)
) engine=InnoDB;

-- 받은 Kafka 메시지를 처리했다고 남기는 인박스. 같은 메시지(그룹 + message-id)가 다시 오면 건너뛴다
create table if not exists board_inbox_kafka_event (
	id bigint not null auto_increment,
	message_id varchar(100) not null,
	consumer_group varchar(100) not null,
	topic varchar(100) not null,
	processed_date datetime(6) not null,
	create_date datetime(6),
	modify_date datetime(6),
	primary key (id),
	constraint uk_board_inbox_group_message unique (consumer_group, message_id)
) engine=InnoDB;
