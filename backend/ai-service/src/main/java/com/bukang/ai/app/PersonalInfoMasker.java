package com.bukang.ai.app;

import java.util.regex.Pattern;

/**
 * 회원이 문의 본문에 직접 쓴 연락처·계좌를 모델에 보내기 전에 가린다 (기획 5장: 휴대폰은 뒤 4자리, 이메일은 앞 2글자)
 * shortcut: 글로 쓴 이름·주소는 모양이 정해져 있지 않아 가리지 못한다. 모델 회사에 남기면 안 되는 일이 생기면 형태소 분석이나 모델 판별을 더한다
 */
final class PersonalInfoMasker {
	private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9._%+-]{1,2})[A-Za-z0-9._%+-]*@[A-Za-z0-9.-]+");
	private static final Pattern PHONE = Pattern.compile("01[016789][-. ]?\\d{3,4}[-. ]?(\\d{4})");
	// 계좌·카드처럼 숫자 10개 이상이 이어진 것 (사이 붙임표 허용). 주문번호(OD-날짜-번호)는 남긴다
	private static final Pattern LONG_NUMBER = Pattern.compile("(?<!OD-)(?<![\\d-])\\d(?:-?\\d){9,}");

	private PersonalInfoMasker() {
	}

	static String mask(String text) {
		if (text == null) {
			return null;
		}
		String masked = EMAIL.matcher(text).replaceAll("$1***@***");
		masked = PHONE.matcher(masked).replaceAll("***-****-$1");
		return LONG_NUMBER.matcher(masked).replaceAll("(숫자 가림)");
	}
}
