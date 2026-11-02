package com.bukang.member.crew.out;

import java.util.Collection;
import java.util.Map;

/**
 * 크루 영역이 회원 정보를 읽는 유일한 길. 회원 쪽(com.bukang.member.out)이 구현한다
 * 크루를 다른 서비스로 떼어 내면 이 구현만 HTTP 호출로 바꾼다
 */
public interface CrewMemberProfilePort {
	// 회원 ID → 닉네임. 찾지 못한 회원 ID는 결과에 없다
	Map<Long, String> findNicknames(Collection<Long> memberIds);

	// 회원 행을 잠그고 탈퇴하지 않은 회원이면 true. 가입과 회원 탈퇴가 겹치지 않게 한다
	// (회원 탈퇴도 같은 행을 잠그고 크루원 행을 지우므로, 둘 중 하나가 커밋된 뒤에 다른 하나가 진행된다)
	boolean lockActiveMember(long memberId);
}
