-- 사용자 취소의 환불 사유 (BUK-74). 취소를 시작할 때 정해 두어, 환불 재처리가 마무리해도 정산 환불 알림에 같은 사유를 담는다
-- 이미 있는 행은 비워 둔다. 비어 있는 취소 중을 마무리하면 구매자 취소로 알린다 (OrderCancelUseCase.refundReason)
alter table order_order_item add column cancel_reason varchar(20);
