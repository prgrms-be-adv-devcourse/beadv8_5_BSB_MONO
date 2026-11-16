-- 결제 잠금 부하 측정용 데이터. 서비스들이 Flyway로 표를 만든 뒤 빈 DB에 한 번 돌린다 (run.sh)
-- ID는 측정용으로 크게 고정한다: 판매자 900001, 대회 900001, 종목 900001~900005, 키트 옵션 900001~900020, 회원 100001~
-- 회원 수는 @members (run.sh가 앞에 set @members = N; 을 붙인다. 없으면 140000)

set @members = coalesce(@members, 140000);
set session cte_max_recursion_depth = 1000000;

-- 1) 상품: 판매자, 대회(개인 예매 열림, 판매 중), 종목 5개(정원 넉넉히, 키트 포함), 키트 옵션 20개(이름 하나, 값 S01~S20)
insert into product_member (id, nickname, create_date, modify_date) values (900001, 'load-seller', now(), now());
insert into product_seller (id, business_name, business_number, representative_name, requested_at, reviewed_at,
	seller_type, status, member_id)
values (900001, '측정 판매자', '000-00-00000', '측정', now(), now(), 'INDIVIDUAL', 'APPROVED', 900001);

insert into race (race_id, created_at, updated_at, application_close_at, crew_min_count, individual_open_at, location,
	race_date, refund_deadline, region_sido, region_sigungu, sale_open_date, sale_status, title, seller_id)
values (900001, now(), now(), now() + interval 30 day, 5, now() - interval 1 day, '측정 경기장',
	now() + interval 60 day, curdate() + interval 50 day, '서울', '성동구', curdate() - interval 1 day, 'ON_SALE',
	'[측정] 결제 잠금 대회', 900001);

insert into race_course (race_course_id, created_at, updated_at, course_type, distance_km, individual_quota,
	kit_included, kit_price, min_age, name, price, status, surface, time_limit_minutes, race_id)
with recursive seq (n) as (select 1 union all select n + 1 from seq where n < 5)
select 900000 + n, now(), now(), 'KM_10', 10.00, 1000000, true, 10000, 10, concat('측정 10K-', n), 50000, 'OPEN',
	'ROAD', 90, 900001
from seq;

insert into product_kit_option (kit_option_id, created_at, updated_at, name, stock, `value`, race_id)
with recursive seq (n) as (select 1 union all select n + 1 from seq where n < 20)
select 900000 + n, now(), now(), '티셔츠', 1000000, concat('S', lpad(n, 2, '0')), 900001
from seq;

-- 2) 예치금: 수수료(1)·보관(2) 회원과 측정 회원, 지갑 잔액 1,000만 원 (지갑 ID = 회원 ID)
insert ignore into cash_member (id, nickname, create_date, modify_date) values
	(1, 'system', now(), now()), (2, 'holding', now(), now());
insert ignore into cash_wallet (id, holder_id, balance, version, create_date, modify_date) values
	(1, 1, 0, 0, now(), now()), (2, 2, 0, 0, now(), now());

insert into cash_member (id, nickname, create_date, modify_date)
with recursive seq (n) as (select 100001 union all select n + 1 from seq where n < 100000 + @members)
select n, concat('load', n), now(), now() from seq;

insert into cash_wallet (id, holder_id, balance, version, create_date, modify_date)
with recursive seq (n) as (select 100001 union all select n + 1 from seq where n < 100000 + @members)
select n, n, 10000000, 0, now(), now() from seq;

select (select count(*) from race_course where race_id = 900001) as courses,
	(select count(*) from product_kit_option where race_id = 900001) as kit_options,
	(select count(*) from cash_wallet where id > 100000) as load_wallets,
	(select sum(balance) from cash_wallet) as money_total;
