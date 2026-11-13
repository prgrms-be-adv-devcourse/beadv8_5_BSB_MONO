-- 보낸 초대 목록에서 링크를 다시 보여 주려고 토큰 원문도 저장한다 (BUK-195)
-- 찾기는 그대로 token_hash로 한다. 이전에 만든 초대는 원문이 없어 NULL로 남는다
ALTER TABLE member_crew_invite ADD COLUMN invite_token VARCHAR(22) NULL AFTER token_hash;
