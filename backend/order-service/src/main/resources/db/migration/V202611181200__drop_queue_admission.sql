-- 주문이 대기열 입장 끝 시각을 저장하지 않는다 (CRU-53)
-- 신청서 타이머 조회와 [결제하기]가 대기열 내부 API를 매번 직접 묻는다. 대기열이 끝 시각을 다시 채우면(TTL 리셋) 바로 따른다
drop table if exists order_queue_admission;
