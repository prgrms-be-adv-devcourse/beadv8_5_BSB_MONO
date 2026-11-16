-- 상품 표의 ID 칸 8개를 엔티티(long)에 맞춰 bigint로 바꾼다 (BUK-218)
-- 10/30 엔티티 ID가 int → long이 됐지만(3f7a45d1, d28b3b86) 그때 운영은 ddl-auto: update라 칸 타입이 바뀌지 않았다
-- V1(create table if not exists)은 표가 이미 있는 DB에서 건너뛰어, validate가 int 칸을 만나 앱이 뜨지 않았다 (11/14 운영 장애)
-- 운영은 11/14에 이 문장들을 손으로 먼저 적용했다. 이 파일은 팀원 로컬 DB와 앞으로 만들 DB에도 같은 변경을 넣는다
-- 이미 bigint인 DB(새 DB, 고친 운영)에서 돌아도 외래 키를 같은 이름으로 다시 만들고 칸을 같은 정의로 다시 쓸 뿐이라 결과가 같다
-- 칸 정의(not null, auto_increment)는 V1과 같게 적는다. modify는 적지 않은 속성을 지운다
-- 한 문장에 칸 하나씩 바꾼다. 테스트의 H2(MySQL 호환 모드)는 한 alter에 여러 modify를 받지 않는다

-- 1. 외래 키를 잠시 지운다. MySQL 8.4는 외래 키가 걸린 칸의 타입을 바꾸지 못한다 (foreign_key_checks = 0으로도 안 된다)
alter table product_seller drop foreign key FKbaugo4e5w2q0qwmtl8rhvt9sh;
alter table race drop foreign key FK2lkphq1okqohgmyrsh44lr5j2;
alter table race_course drop foreign key FK2ydanu4kknm3a965sqhihko6u;
alter table participation_hold drop foreign key FKshkq8c3gx7o6y5vr5f3xxw83t;

-- 2. int로 남았던 칸을 bigint로 바꾼다
alter table product_member modify id bigint not null;
alter table product_seller modify id bigint not null;
alter table product_seller modify member_id bigint not null;
alter table race modify race_id bigint not null auto_increment;
alter table race modify seller_id bigint not null;
alter table race_course modify race_course_id bigint not null auto_increment;
alter table race_course modify race_id bigint not null;
alter table participation_hold modify race_course_id bigint not null;

-- 3. 외래 키를 V1과 같은 이름으로 다시 만든다
alter table product_seller add constraint FKbaugo4e5w2q0qwmtl8rhvt9sh foreign key (member_id) references product_member (id);
alter table race add constraint FK2lkphq1okqohgmyrsh44lr5j2 foreign key (seller_id) references product_seller (id);
alter table race_course add constraint FK2ydanu4kknm3a965sqhihko6u foreign key (race_id) references race (race_id);
alter table participation_hold add constraint FKshkq8c3gx7o6y5vr5f3xxw83t foreign key (race_course_id) references race_course (race_course_id);
