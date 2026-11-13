-- 정확성 확인 C1~C5 (판정 기준 문서 1장). 측정이 끝나고 결제가 모두 멈춘 뒤 돌린다
-- 결과는 이름=값 한 줄씩. @money_start는 run.sh가 앞에 붙인다 (시드 직후 지갑 잔액 합)

select concat('C1_money_total_diff=', sum(balance) - @money_start) from cash_wallet;

select concat('C2_double_charge_orders=', count(*)) from (
	select ref_id from cash_wallet_ledger
	where ref_type_code = 'Order' and ledger_type = '사용__주문결제'
	group by ref_id having count(*) > 1) t;

select concat('C3_kit_option_over_stock=', count(*))
from order_kit_option_sale s join product_kit_option o on o.kit_option_id = s.kit_option_id
where s.sold > o.stock;

select concat('C4_paid_orders=', (select count(*) from order_order where status = 'PAID'));
select concat('C4_cash_completed_payments=', (select count(*) from cash_payment where status = 'COMPLETED'));
select concat('C4_sold_holds=', (select count(*) from participation_hold where status = 'SOLD'));

-- 예치금은 결제 완료인데 주문은 결제 완료가 아닌 것. B(결제 중)는 주문 상태 값이 늘어도 PAID가 아니면 여기서 잡힌다
select concat('C5_stuck_payments=', count(*))
from cash_payment p left join order_order o on o.id = p.order_id
where p.status = 'COMPLETED' and (o.id is null or o.status <> 'PAID');

select concat('INFO_orders_by_status=', group_concat(concat(status, ':', c) order by status))
from (select status, count(*) c from order_order group by status) t;
select concat('INFO_payments_by_status=', group_concat(concat(status, ':', c) order by status))
from (select status, count(*) c from cash_payment group by status) t;
