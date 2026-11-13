-- 크루에서 내보낸 기록 (BUK-135). 초대 링크 하나로 여러 명이 가입하게 바뀌어,
-- 내보낸 회원이 내보내기 전에 만든 링크로 다시 들어오는 것을 막는 데 쓴다
-- 크루 영역 테이블처럼 회원·크루에 외래 키를 걸지 않고 ID 값만 둔다
CREATE TABLE member_crew_removal (
	removal_id BIGINT NOT NULL AUTO_INCREMENT,
	crew_id BIGINT NOT NULL,
	member_id BIGINT NOT NULL,
	removed_at DATETIME(6) NOT NULL,
	PRIMARY KEY (removal_id)
);

-- 가입할 때 「이 회원을 이 초대보다 뒤에 내보냈는지」 찾는다
CREATE INDEX idx_crew_removal_crew_member ON member_crew_removal (crew_id, member_id, removed_at);
