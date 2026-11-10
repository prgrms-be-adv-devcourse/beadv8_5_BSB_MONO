-- cash 표의 ID·금액 칸을 int에서 bigint로, 결제 키 암호문 칸을 512로 넓힌다
-- 엔티티는 2026-10-30에 ID·금액을 long으로 바꿨지만, ddl-auto: update는 칸 타입·길이를 바꾸지 않아 그 전에 만든 서버 표는 int로 남아 있다
-- 그대로 validate로 바꾸면 "found [int], but expecting [bigint]"로 앱이 뜨지 않는다
-- 새 DB는 V1이 이미 bigint로 만들어 아무것도 바뀌지 않는다. 손으로 이미 바꾼 DB에서 돌려도 결과가 같다
-- 외래 키가 걸린 칸은 타입을 바꿀 수 없으므로, 외래 키를 먼저 풀고 타입을 바꾼 뒤 같은 이름으로 다시 건다
-- 10-30 뒤에 생긴 표(환급 몫 등)는 bigint 칸이 int ID를 가리켜 update가 외래 키를 만들지 못했을 수 있다
-- 그래서 외래 키는 있을 때만 풀고, 마지막에 9개를 모두 다시 건다 (없던 외래 키도 이때 생긴다)
-- 값은 넓어지기만 하므로 기존 데이터는 그대로 남는다. auto_increment 다음 값도 유지된다

-- 1. 외래 키가 있으면 푼다 (이름은 V1·Hibernate가 만든 이름)
set @sql = (select if(count(*) > 0, 'alter table cash_seller drop foreign key FKlcd2tgmayhtg7gwqmjiovctdv', 'select 1')
	from information_schema.table_constraints where table_schema = database() and constraint_name = 'FKlcd2tgmayhtg7gwqmjiovctdv');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
set @sql = (select if(count(*) > 0, 'alter table cash_wallet drop foreign key FKm14pbd3uf8mc3ndoxo7lwklfg', 'select 1')
	from information_schema.table_constraints where table_schema = database() and constraint_name = 'FKm14pbd3uf8mc3ndoxo7lwklfg');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
set @sql = (select if(count(*) > 0, 'alter table cash_wallet_charge drop foreign key FKskxr1hoe5vjnfg4tyv3n1b2xu', 'select 1')
	from information_schema.table_constraints where table_schema = database() and constraint_name = 'FKskxr1hoe5vjnfg4tyv3n1b2xu');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
set @sql = (select if(count(*) > 0, 'alter table cash_wallet_ledger drop foreign key FK9w7q4uxm83x2aabwpsdx3tj3c', 'select 1')
	from information_schema.table_constraints where table_schema = database() and constraint_name = 'FK9w7q4uxm83x2aabwpsdx3tj3c');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
set @sql = (select if(count(*) > 0, 'alter table cash_wallet_ledger drop foreign key FKjoat1008msx2gbvu49lcqva4g', 'select 1')
	from information_schema.table_constraints where table_schema = database() and constraint_name = 'FKjoat1008msx2gbvu49lcqva4g');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
set @sql = (select if(count(*) > 0, 'alter table cash_payment drop foreign key FKl07hu49ujvw1h9oq5la5qakg2', 'select 1')
	from information_schema.table_constraints where table_schema = database() and constraint_name = 'FKl07hu49ujvw1h9oq5la5qakg2');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
set @sql = (select if(count(*) > 0, 'alter table cash_wallet_withdrawal drop foreign key FK4milqw7fxl6hqfmuimlsfh3x7', 'select 1')
	from information_schema.table_constraints where table_schema = database() and constraint_name = 'FK4milqw7fxl6hqfmuimlsfh3x7');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
set @sql = (select if(count(*) > 0, 'alter table cash_wallet_withdrawal_item drop foreign key FK7b4q0q8c0hpbvv4b7mth22ylt', 'select 1')
	from information_schema.table_constraints where table_schema = database() and constraint_name = 'FK7b4q0q8c0hpbvv4b7mth22ylt');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
set @sql = (select if(count(*) > 0, 'alter table cash_wallet_withdrawal_item drop foreign key FK80kvs5vomufqk8o42ccqo6bl4', 'select 1')
	from information_schema.table_constraints where table_schema = database() and constraint_name = 'FK80kvs5vomufqk8o42ccqo6bl4');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- 2. 회원·판매자 복제본
alter table cash_member modify id bigint not null;
alter table cash_seller modify id bigint not null;
alter table cash_seller modify member_id bigint not null;

-- 3. 지갑
alter table cash_wallet modify id bigint not null;
alter table cash_wallet modify holder_id bigint not null;
alter table cash_wallet modify balance bigint not null;
alter table cash_wallet modify version bigint not null;

-- 4. 충전 (결제 키 암호문은 255로는 잘린다)
alter table cash_wallet_charge modify id bigint not null auto_increment;
alter table cash_wallet_charge modify wallet_id bigint not null;
alter table cash_wallet_charge modify amount bigint not null;
alter table cash_wallet_charge modify canceled_amount bigint not null;
alter table cash_wallet_charge modify pg_payment_key varchar(512);

-- 5. 원장
alter table cash_wallet_ledger modify id bigint not null auto_increment;
alter table cash_wallet_ledger modify holder_id bigint not null;
alter table cash_wallet_ledger modify wallet_id bigint not null;
alter table cash_wallet_ledger modify ref_id bigint not null;
alter table cash_wallet_ledger modify amount bigint not null;
alter table cash_wallet_ledger modify balance_after bigint not null;

-- 6. 주문 결제
alter table cash_payment modify id bigint not null auto_increment;
alter table cash_payment modify wallet_id bigint not null;
alter table cash_payment modify order_id bigint not null;
alter table cash_payment modify amount bigint not null;

-- 7. 환급·환급 몫 (계좌번호 암호문 길이는 V202611101500이 맞춘다)
alter table cash_wallet_withdrawal modify id bigint not null auto_increment;
alter table cash_wallet_withdrawal modify wallet_id bigint not null;
alter table cash_wallet_withdrawal modify amount bigint not null;
alter table cash_wallet_withdrawal_item modify id bigint not null auto_increment;
alter table cash_wallet_withdrawal_item modify withdrawal_id bigint not null;
alter table cash_wallet_withdrawal_item modify charge_id bigint not null;
alter table cash_wallet_withdrawal_item modify amount bigint not null;

-- 8. 정산금 송금
alter table cash_payout_transfer modify id bigint not null auto_increment;
alter table cash_payout_transfer modify payout_id bigint not null;
alter table cash_payout_transfer modify seller_id bigint not null;
alter table cash_payout_transfer modify amount bigint not null;

-- 9. 외래 키를 다시 건다
alter table cash_seller add constraint FKlcd2tgmayhtg7gwqmjiovctdv foreign key (member_id) references cash_member (id);
alter table cash_wallet add constraint FKm14pbd3uf8mc3ndoxo7lwklfg foreign key (holder_id) references cash_member (id);
alter table cash_wallet_charge add constraint FKskxr1hoe5vjnfg4tyv3n1b2xu foreign key (wallet_id) references cash_wallet (id);
alter table cash_wallet_ledger add constraint FK9w7q4uxm83x2aabwpsdx3tj3c foreign key (holder_id) references cash_member (id);
alter table cash_wallet_ledger add constraint FKjoat1008msx2gbvu49lcqva4g foreign key (wallet_id) references cash_wallet (id);
alter table cash_payment add constraint FKl07hu49ujvw1h9oq5la5qakg2 foreign key (wallet_id) references cash_wallet (id);
alter table cash_wallet_withdrawal add constraint FK4milqw7fxl6hqfmuimlsfh3x7 foreign key (wallet_id)
	references cash_wallet (id);
alter table cash_wallet_withdrawal_item add constraint FK7b4q0q8c0hpbvv4b7mth22ylt foreign key (withdrawal_id)
	references cash_wallet_withdrawal (id);
alter table cash_wallet_withdrawal_item add constraint FK80kvs5vomufqk8o42ccqo6bl4 foreign key (charge_id)
	references cash_wallet_charge (id);
