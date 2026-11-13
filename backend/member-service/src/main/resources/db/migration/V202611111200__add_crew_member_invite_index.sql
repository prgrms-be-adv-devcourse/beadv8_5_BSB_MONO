-- 초대 정리(BUK-100)가 가입에 쓴 초대인지 확인할 때 크루원 테이블을 끝까지 읽지 않도록 한다
CREATE INDEX idx_crew_member_invite ON member_crew_member (invite_id);
