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
}
