package com.bukang.member.out;

/**
 * 재발급 때 Redis(refresh-token-rotate.lua)가 내린 판정.
 * 이름은 Lua 스크립트가 돌려주는 문자열과 같아야 한다.
 */
public enum RotationOutcome {
	/** 지금 토큰이 왔다. 새 토큰으로 바꿨다 */
	ROTATED,
	/** 방금 바뀐 바로 전 토큰이 유예 시간 안에 왔다. 지금 토큰의 jti와 만료 시각을 돌려준다 */
	GRACE,
	/** 같은 family의 이미 쓴 토큰이 왔다. family를 지웠다 */
	THEFT,
	/** family가 다르다 (다른 기기에서 새로 로그인함). 아무것도 바꾸지 않았다 */
	STALE,
	/** 기록이 없다 (로그아웃, 탈취 처리, TTL 만료). 배포 전 옛 기록에서 유예가 온 경우도 같다 */
	MISSING
}
