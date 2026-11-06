-- 파티원 결제 마감 알림을 한 번만 보내려고 보낸 시각을 남긴다 (BUK-38). 비어 있으면 아직 안 보냄
alter table order_party add column payment_reminded_at datetime(6);
