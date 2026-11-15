-- 크루 파티 만들기 제한 (BUK-187). 한 달에 결제율 미달이 3회인 크루는 30일 동안 파티를 만들 수 없다
-- 상품 서비스가 알려 준 미달 크루를 매일 저장한다. 크루·달마다 한 행만 두어, 같은 달을 다시 받아도 제한 시작일이 밀리지 않는다
-- 상태 컬럼은 없다. start_at <= 지금 < end_at인 행이 하나라도 있으면 제한 중이다
-- 크루 영역 테이블처럼 크루에 외래 키를 걸지 않고 ID 값만 둔다
CREATE TABLE member_crew_penalty (
	crew_penalty_id BIGINT NOT NULL AUTO_INCREMENT,
	crew_id BIGINT NOT NULL,
	penalty_month CHAR(7) NOT NULL,
	low_payment_count INT NOT NULL,
	start_at DATETIME(6) NOT NULL,
	end_at DATETIME(6) NOT NULL,
	created_at DATETIME(6) NOT NULL,
	updated_at DATETIME(6) NOT NULL,
	PRIMARY KEY (crew_penalty_id),
	CONSTRAINT uk_crew_penalty_crew_month UNIQUE (crew_id, penalty_month)
);

-- 파티를 만들 때와 크루 홈에서 「이 크루가 지금 제한 중인지」 찾는다
CREATE INDEX idx_crew_penalty_crew_end ON member_crew_penalty (crew_id, end_at);
