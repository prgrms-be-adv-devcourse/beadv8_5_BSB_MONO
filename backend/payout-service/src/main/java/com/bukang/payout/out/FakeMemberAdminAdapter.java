package com.bukang.payout.out;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 테스트용 어댑터: 회원 서비스를 띄우지 않는 테스트 프로파일에서 누구든 관리자로 본다 (PRO-265)
 * 권한 검사 테스트(관리자 API 컨트롤러 테스트)는 이 빈 대신 결과를 정한 목(@MockitoBean)을 넣으므로,
 * 이 어댑터는 스프링 컨텍스트를 띄우기 위해서만 있다
 * dev(로컬)와 prod는 MemberAdminAdapter가 실제 회원 서비스를 부른다
 */
@Component
@Profile("test")
public class FakeMemberAdminAdapter implements MemberAdminPort {
	@Override
	public boolean isAdmin(long memberId) {
		return true;
	}
}
