package com.bukang.order.out.member;

import java.util.Map;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 가짜 어댑터: 회원 서비스에 닉네임 조회 API가 생기기 전까지 개발용으로 답한다 (BUK-26)
 * 회원 서비스 시작 데이터(MemberDataInit)가 만드는 순서대로 회원 ID를 정해 두었다. 빈 dev DB에서 처음 띄우면 이 ID가 된다
 * 그 밖의 닉네임은 없는 회원으로 답한다. 운영(prod)에서는 쓰지 않는다 (order.clients.nickname.mode=disabled)
 * mode를 fake라고 적었을 때만 켜진다. 설정이 빠지면 엉뚱한 회원을 찾는 대신 빈이 없어 앱이 뜨지 않는다
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "order.clients.nickname", name = "mode", havingValue = "fake")
public class FakeMemberNicknameAdapter implements MemberNicknamePort {
	static final Map<String, Long> SEED_MEMBER_IDS = Map.of(
		"관리자", 3L,
		"사용자1", 4L,
		"사용자2", 5L,
		"사용자3", 6L,
		"판매자1", 7L
	);

	// 호출마다 경고를 남기면 로그가 묻히므로, 앱을 켤 때 한 번만 알린다
	public FakeMemberNicknameAdapter() {
		log.warn("닉네임 → 회원 ID는 가짜 응답(FakeMemberNicknameAdapter)을 씁니다. 회원 서비스 시작 데이터의 닉네임만 찾습니다");
	}

	@Override
	public Optional<Long> findMemberId(String nickname) {
		return Optional.ofNullable(SEED_MEMBER_IDS.get(nickname));
	}
}
