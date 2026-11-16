-- 크루 결제율 미달 집계용 인덱스 (BUK-176)
-- 한 달 동안 확보한 크루 자리(crew_id가 있는 행)를 확보 일시(held_at)로 찾는다
-- Flyway가 한 번만 실행하므로 V1과 달리 if not exists 없이 만듭니다
create index idx_participation_hold_crew_held on participation_hold (crew_id, held_at);
