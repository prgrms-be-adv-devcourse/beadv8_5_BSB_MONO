-- 종목 참가 나이를 「성인만 받기」 하나로 줄인다 (CRU-54). 최소 나이는 14(제한 없음)·19(성인만)만, 최대 나이는 항상 null
-- 기존 종목은 19 이상이면 19, 그보다 낮으면 14로 맞춘다
update race_course set min_age = 19 where min_age >= 19;
update race_course set min_age = 14 where min_age < 19;
update race_course set max_age = null where max_age is not null;
