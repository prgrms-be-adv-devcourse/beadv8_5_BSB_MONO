-- 정산 서비스 표의 integer ID 칸을 bigint로 바꾼다 (#260 모든 서비스 ID long 통일, PR #265)
-- update는 칸 타입을 바꾸지 않아 운영 표는 #260 배포 뒤에도 integer로 남아 있다 (2026-11-02 운영 DB 확인)
-- 새 DB는 V1이 integer로 만든 표를, 운영 · 로컬 DB는 이미 있는 표를 여기서 함께 바꾼다. 이미 bigint인 칸은 그대로 bigint가 된다
-- 외래 키가 걸린 칸은 타입을 바꿀 수 없으므로, 외래 키를 먼저 풀고 타입을 바꾼 뒤 같은 이름으로 다시 건다
-- 값은 integer에서 bigint로 넓어지기만 하므로 기존 데이터는 그대로 남는다. auto_increment 다음 값도 유지된다
-- 한 문장에 modify를 여러 개 쓰지 않는다. 테스트용 H2(MySQL 모드)가 읽지 못한다

-- 1. 외래 키를 푼다 (이름은 V1 · Hibernate가 만든 이름)
alter table payout_payout_item drop foreign key FKajawje20wapiqqr1tqowiyg17;
alter table payout_payout_item drop foreign key FKhguq1wqmgkbrraskt1433ixus;
alter table payout_payout_hold drop foreign key FK1pu68hpwtmd4rwlad59ti9sr4;
alter table payout_payout_status_history drop foreign key FK97y0g5iyy8mtwshi8m2thq113;

-- 2. 정산 내역 · 회수 ID
alter table payout_payout modify id bigint not null auto_increment;
alter table payout_payout_recovery modify id bigint not null auto_increment;

-- 3. 정산 내역 · 회수를 가리키는 칸
alter table payout_payout_item modify payout_id bigint;
alter table payout_payout_item modify recovery_id bigint;
alter table payout_payout_hold modify payout_id bigint not null;
alter table payout_payout_status_history modify payout_id bigint not null;

-- 4. 외래 키를 같은 이름으로 다시 건다
alter table payout_payout_item
	add constraint FKajawje20wapiqqr1tqowiyg17 foreign key (recovery_id) references payout_payout_recovery (id);
alter table payout_payout_item
	add constraint FKhguq1wqmgkbrraskt1433ixus foreign key (payout_id) references payout_payout (id);
alter table payout_payout_hold
	add constraint FK1pu68hpwtmd4rwlad59ti9sr4 foreign key (payout_id) references payout_payout (id);
alter table payout_payout_status_history
	add constraint FK97y0g5iyy8mtwshi8m2thq113 foreign key (payout_id) references payout_payout (id);
