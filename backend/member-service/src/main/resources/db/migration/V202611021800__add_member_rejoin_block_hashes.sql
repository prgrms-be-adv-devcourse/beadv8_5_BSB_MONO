-- 탈퇴 후 30일 재가입 제한 (BUK-33, 11/2 지훈 님 결정)
-- 탈퇴하면 identity_hash · ci_hash를 비우고(유니크 제약 때문에 30일 뒤 재가입이 막히지 않게) 이 두 컬럼으로 옮긴다
-- 재가입하려는 사람의 해시가 30일 안에 탈퇴한 회원의 이 값과 같으면 가입을 막는다
-- 30일이 지나면 매일 도는 정리 작업이 비운다. 유니크가 아니라 인덱스만 둔다 (같은 사람이 여러 번 탈퇴할 수 있다)

ALTER TABLE member_member ADD COLUMN withdrawn_identity_hash VARCHAR(64) NULL;
ALTER TABLE member_member ADD COLUMN withdrawn_ci_hash VARCHAR(64) NULL;
CREATE INDEX idx_member_withdrawn_identity_hash ON member_member (withdrawn_identity_hash);
CREATE INDEX idx_member_withdrawn_ci_hash ON member_member (withdrawn_ci_hash);
