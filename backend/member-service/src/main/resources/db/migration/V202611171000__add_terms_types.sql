-- 약관 종류에 만 14세 이상(AGE_OVER_14), 야간 광고 수신(NIGHT_MARKETING), 판매자(SELLER)를 더한다 (CRU-22)
-- enum 값 목록만 넓히므로 기존 약관·동의 기록은 그대로 남는다. 기본 약관 데이터는 TermsDataInit이 앱 시작 때 넣는다
ALTER TABLE member_terms MODIFY type ENUM ('MARKETING', 'PRIVACY', 'SERVICE', 'AGE_OVER_14', 'NIGHT_MARKETING', 'SELLER') NOT NULL;
