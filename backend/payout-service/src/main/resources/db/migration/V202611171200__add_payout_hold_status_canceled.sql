-- 지급보류 상태에 취소를 더한다 (BUK-168, PR 569 리뷰)
-- 대회 취소로 정산 내역을 닫을 때 진행 중 보류도 함께 닫는다. 돈이 나가지 않으므로 해제와 나눈다
alter table payout_payout_hold modify status enum('지급보류','해제','취소') not null;

-- 이미 취소로 닫힌 정산 내역에 남은 보류를 닫는다. 대회 취소는 시스템(0)이 닫았고, 시각은 정산 내역을 마지막으로 바꾼 때로 둔다
-- 테스트의 H2도 읽도록 UPDATE ... JOIN 대신 하위 쿼리로 쓴다
update payout_payout_hold
set status = '취소', released_by = 0,
	released_at = (select p.modify_date from payout_payout p where p.id = payout_payout_hold.payout_id)
where status = '지급보류' and payout_id in (select p.id from payout_payout p where p.status = '취소');
