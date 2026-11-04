-- 주문 서비스 표의 ID·금액 칸을 integer에서 bigint로 바꾼다 (#260 모든 서비스 ID·금액 long 통일, BUK-29)
-- 엔티티의 long과 맞춰 ddl-auto: validate를 통과한다. 수량(quantity)과 재시도 횟수(retry_count)는 int 그대로 둔다
-- 새 DB는 V1이 integer로 만든 표를, 이미 있는 서버·로컬 DB는 ddl-auto: update가 만든 표를 여기서 함께 바꾼다
-- (update는 칸 타입을 바꾸지 않아 서버 표는 #260 배포 뒤에도 integer로 남아 있다). 이미 bigint인 칸은 그대로 bigint가 된다
-- 외래 키가 걸린 칸은 타입을 바꿀 수 없으므로, 외래 키를 먼저 풀고 타입을 바꾼 뒤 같은 이름으로 다시 건다
-- 값은 integer에서 bigint로 넓어지기만 하므로 기존 데이터는 그대로 남는다. auto_increment 다음 값도 유지된다
-- 한 문장에 modify를 여러 개 쓰지 않는다. 테스트용 H2(MySQL 모드)가 읽지 못한다

-- 1. 외래 키를 푼다 (이름은 V1·Hibernate가 만든 이름)
alter table order_cart_item drop foreign key FKo91wwyi74i23yxss7qtyjv4nq;
alter table order_order_item drop foreign key FKg5w1jqkpwikvtlb8g2vim8chh;
alter table order_shipment drop foreign key FK4j6ri1yutxraql35f56d7hfr5;
alter table order_shipment_item drop foreign key FK2it8xvxkx09peyi935pqe2xfc;

-- 2. 장바구니
alter table order_cart modify id bigint not null auto_increment;
alter table order_cart modify member_id bigint not null;
alter table order_cart_item modify id bigint not null auto_increment;
alter table order_cart_item modify cart_id bigint not null;
alter table order_cart_item modify race_course_id bigint not null;

-- 3. 주문
alter table order_order modify id bigint not null auto_increment;
alter table order_order modify buyer_id bigint not null;
alter table order_order modify race_id bigint not null;
alter table order_order modify total_amount bigint not null;
alter table order_order modify canceled_amount bigint not null;

-- 4. 주문 상품
alter table order_order_item modify id bigint not null auto_increment;
alter table order_order_item modify order_id bigint not null;
alter table order_order_item modify participation_id bigint not null;
alter table order_order_item modify race_id bigint not null;
alter table order_order_item modify race_course_id bigint not null;
alter table order_order_item modify seller_id bigint not null;
alter table order_order_item modify unit_price bigint not null;
alter table order_order_item modify amount bigint not null;
alter table order_order_item modify canceled_amount bigint not null;

-- 5. 보낼 메시지 함
alter table order_outbox_kafka_event modify id bigint not null auto_increment;
alter table order_outbox_kafka_event modify ref_id bigint not null;

-- 6. 참가 신청
alter table order_participation modify id bigint not null auto_increment;
alter table order_participation modify member_id bigint not null;
alter table order_participation modify race_id bigint not null;
alter table order_participation modify race_course_id bigint not null;
alter table order_participation modify crew_application_id bigint;

-- 7. 대기열 입장 기록
alter table order_queue_admission modify id bigint not null auto_increment;
alter table order_queue_admission modify member_id bigint not null;
alter table order_queue_admission modify race_course_id bigint not null;

-- 8. 키트 배송
alter table order_shipment modify id bigint not null auto_increment;
alter table order_shipment modify order_id bigint not null;
alter table order_shipment_item modify id bigint not null auto_increment;
alter table order_shipment_item modify shipment_id bigint not null;
alter table order_shipment_item modify order_item_id bigint not null;

-- 9. 외래 키를 다시 건다
alter table order_cart_item
	add constraint FKo91wwyi74i23yxss7qtyjv4nq foreign key (cart_id) references order_cart (id);
alter table order_order_item
	add constraint FKg5w1jqkpwikvtlb8g2vim8chh foreign key (order_id) references order_order (id);
alter table order_shipment
	add constraint FK4j6ri1yutxraql35f56d7hfr5 foreign key (order_id) references order_order (id);
alter table order_shipment_item
	add constraint FK2it8xvxkx09peyi935pqe2xfc foreign key (shipment_id) references order_shipment (id);
