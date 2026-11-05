package com.bukang.member.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.member.config.KakaoInviteProperties;
import com.bukang.member.crew.app.CrewFacade;
import com.bukang.member.crew.domain.CrewMember;
import com.bukang.member.crew.dto.CrewInviteCreatedDto;
import com.bukang.member.crew.out.CrewMemberProfilePort;
import com.bukang.member.crew.out.CrewMemberRepository;
import com.bukang.member.domain.MemberStatus;
import com.bukang.member.domain.SocialProvider;
import com.bukang.member.domain.exception.KakaoMessageConsentRequiredException;
import com.bukang.member.domain.exception.KakaoMessageDailyLimitException;
import com.bukang.member.domain.exception.KakaoUnavailableException;
import com.bukang.member.dto.KakaoFriendDto;
import com.bukang.member.dto.KakaoInviteSendResultDto;
import com.bukang.member.dto.KakaoInviteSendResultDto.Failure;
import com.bukang.member.dto.KakaoInviteSendResultDto.Reason;
import com.bukang.member.out.SocialAccountRepository;
import com.bukang.member.out.kakao.KakaoApiException;
import com.bukang.member.out.kakao.KakaoFriend;
import com.bukang.member.out.kakao.KakaoTalkClient;

import lombok.RequiredArgsConstructor;

/**
 * 크루 초대 카카오톡 보내기 (kakao-crew-invite 결정 3)
 * 카카오를 부르는 동안 DB 연결을 붙잡지 않도록 이 클래스에는 트랜잭션을 걸지 않는다
 * 크루 검사·초대 링크 만들기·취소는 CrewFacade가, 토큰은 KakaoMessageTokens가 각자 짧은 트랜잭션으로 한다
 */
@Service
@RequiredArgsConstructor
public class KakaoCrewInviteUseCase {
	private static final String DEFAULT_SENDER = "크루원";

	private final CrewFacade crewFacade;
	private final CrewMemberRepository crewMemberRepository;
	private final CrewMemberProfilePort profilePort;
	private final SocialAccountRepository socialAccountRepository;
	private final KakaoMessageTokens kakaoMessageTokens;
	private final KakaoTalkClient kakaoTalkClient;
	private final KakaoInviteProperties properties;
	private final JsonConverter jsonConverter;

	// 카카오 친구 중 크루런 회원이고 아직 이 크루원이 아닌 사람. 친구 정보는 저장하지 않는다
	public List<KakaoFriendDto> findFriends(long memberId, long crewId) {
		crewFacade.findInvitableCrewName(memberId, crewId);
		String accessToken = kakaoMessageTokens.accessToken(memberId);
		List<KakaoFriend> friends = callKakao(memberId, () -> kakaoTalkClient.friends(accessToken));
		if (friends.isEmpty()) {
			return List.of();
		}
		Map<String, Long> memberIdsByKakaoId = new HashMap<>();
		for (Object[] row : socialAccountRepository.findMemberIdsByProviderUserIds(SocialProvider.KAKAO,
			friends.stream().map(KakaoFriend::id).toList(), MemberStatus.WITHDRAWN)) {
			memberIdsByKakaoId.put((String)row[0], (Long)row[1]);
		}
		Set<Long> crewMemberIds = crewMemberRepository.findAllByCrewId(crewId).stream()
			.map(CrewMember::getMemberId)
			.collect(Collectors.toSet());
		return friends.stream()
			.filter(friend -> memberIdsByKakaoId.containsKey(friend.id())
				&& !crewMemberIds.contains(memberIdsByKakaoId.get(friend.id())))
			.map(friend -> new KakaoFriendDto(friend.uuid(), friend.nickname(), friend.profileImageUrl()))
			.toList();
	}

	/**
	 * 받는 사람마다 새 초대 링크를 만들어 한 명씩 보낸다. 초대 링크는 한 사람만 쓸 수 있어서 같은 링크를 나눠 보낼 수 없다
	 * 보내지 못한 몫의 링크는 바로 취소해, 초대 목록에 쓸 수 없는 링크가 남지 않게 한다
	 * 한 명도 못 보내면 모두 하루 한도일 때 429, 그 밖은 502다. 토큰 오류면 토큰을 지우고 403으로 다시 동의하게 한다
	 */
	public KakaoInviteSendResultDto send(long memberId, long crewId, List<String> receiverUuids) {
		String crewName = crewFacade.findInvitableCrewName(memberId, crewId);
		String accessToken = kakaoMessageTokens.accessToken(memberId);
		String sender = profilePort.findNicknames(List.of(memberId)).getOrDefault(memberId, DEFAULT_SENDER);

		List<String> sent = new ArrayList<>();
		List<Failure> failures = new ArrayList<>();
		for (int i = 0; i < receiverUuids.size(); i++) {
			String uuid = receiverUuids.get(i);
			CrewInviteCreatedDto invite = crewFacade.createInvite(memberId, crewId);
			try {
				// 초대 토큰 원문은 메시지 안에만 싣고 응답·로그에는 남기지 않는다
				kakaoTalkClient.send(accessToken, uuid, template(crewName, sender, invite.inviteToken()));
				sent.add(uuid);
			} catch (KakaoApiException exception) {
				crewFacade.cancelInvite(memberId, crewId, invite.inviteId());
				if (exception.isTokenProblem()) {
					// 토큰을 지우고 멈춘다. 이미 보낸 사람이 있으면 200으로 알려, 다시 동의한 뒤 같은 사람에게 또 보내지 않게 한다
					kakaoMessageTokens.discard(memberId);
					if (sent.isEmpty()) {
						throw new KakaoMessageConsentRequiredException();
					}
					receiverUuids.subList(i, receiverUuids.size())
						.forEach(rest -> failures.add(new Failure(rest, Reason.KAKAO_ERROR)));
					break;
				}
				failures.add(new Failure(uuid, exception.isDailyLimit() ? Reason.DAILY_LIMIT : Reason.KAKAO_ERROR));
			} catch (KakaoUnavailableException exception) {
				crewFacade.cancelInvite(memberId, crewId, invite.inviteId());
				failures.add(new Failure(uuid, Reason.KAKAO_ERROR));
			}
		}
		if (sent.isEmpty()) {
			if (failures.stream().allMatch(failure -> failure.reason() == Reason.DAILY_LIMIT)) {
				throw new KakaoMessageDailyLimitException();
			}
			throw new KakaoUnavailableException();
		}
		return new KakaoInviteSendResultDto(sent, failures);
	}

	// 카카오 기본 피드 템플릿. 콘솔에 템플릿을 만들 필요가 없다. 링크 도메인은 콘솔 「웹 도메인」에 등록돼 있어야 한다
	private String template(String crewName, String sender, String inviteToken) {
		String url = properties.inviteUrl(inviteToken);
		Map<String, String> link = Map.of("web_url", url, "mobile_web_url", url);
		return jsonConverter.toJson(Map.of(
			"object_type", "feed",
			"content", Map.of(
				"title", crewName + " 크루 초대",
				"description", sender + " 님이 크루런 크루에 초대했어요. 링크는 24시간 동안 쓸 수 있어요.",
				"link", link),
			"buttons", List.of(Map.of("title", "초대 받기", "link", link))));
	}

	// 카카오가 토큰을 받지 않으면(-401·-402) 토큰을 지우고 다시 동의하게 한다. 그 밖의 오류 코드는 카카오 장애로 본다
	private <T> T callKakao(long memberId, Supplier<T> request) {
		try {
			return request.get();
		} catch (KakaoApiException exception) {
			if (exception.isTokenProblem()) {
				kakaoMessageTokens.discard(memberId);
				throw new KakaoMessageConsentRequiredException();
			}
			throw new KakaoUnavailableException();
		}
	}
}
