package com.bukang.order.out.member;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 가짜 어댑터: 회원 서비스를 띄우지 않고 개발할 때 「러너{회원 ID}」로 답한다 (BUK-39)
 * 관리자 확인과 같은 설정(order.clients.member.mode=fake)으로 켜진다. 운영은 http라 HttpMemberNicknamesAdapter가 답한다
 */
@Component
@ConditionalOnProperty(prefix = "order.clients.member", name = "mode", havingValue = "fake")
public class FakeMemberNicknamesAdapter implements MemberNicknamesPort {
	@Override
	public Map<Long, String> findNicknames(Collection<Long> memberIds) {
		return memberIds.stream().distinct().collect(Collectors.toMap(Function.identity(), id -> "러너" + id));
	}
}
