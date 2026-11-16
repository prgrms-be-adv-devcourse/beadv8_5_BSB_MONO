-- 결제를 「결제 중」 저장과 확정으로 나눈다 (BUK-232). status는 varchar(20)이라 PAYING을 그대로 넣을 수 있다
-- 결제 중이 된 시각. 결제 재확인 배치가 오래 남은 결제 중을 고르고, 결제 중에 머문 시간을 잰다
alter table order_order add column pay_started_at datetime(6);
-- 이번 결제 중에 예치금·자리 판매를 부른 횟수
alter table order_order add column pay_attempts int not null default 0;
-- 결제 재확인 배치가 결제 중 주문을 시작 시각 순으로 찾는다
create index idx_order_paying on order_order (status, pay_started_at);
