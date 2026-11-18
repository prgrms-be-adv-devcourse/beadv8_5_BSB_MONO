package com.bukang.member.domain;

// 약관 종류. 가입 때 묻는 약관(askedAtJoin)은 가입 화면이 동의 여부를 보내고, 판매자 약관은 판매자 신청 때 받는다 (BE-021, CRU-22)
public enum TermsType {
	SERVICE(true),
	PRIVACY(true),
	MARKETING(true),
	// 만 14세 이상 확인. 생년월일 검사(Member.isJoinableAge)와 별개로 동의 기록을 남긴다
	AGE_OVER_14(true),
	// 야간(밤 9시~아침 8시) 광고 수신 동의 (선택)
	NIGHT_MARKETING(true),
	SELLER(false);

	private final boolean askedAtJoin;

	TermsType(boolean askedAtJoin) {
		this.askedAtJoin = askedAtJoin;
	}

	public boolean isAskedAtJoin() {
		return askedAtJoin;
	}
}
