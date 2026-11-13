package com.bukang.inquiry.out.member;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.inquiry.domain.exception.InquiryExternalServiceUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원 서비스의 닉네임 여러 명 조회 내부 API를 부른다 (GET /internal/v1/member/members/nicknames?ids=)
 * - 200: data의 [{memberId, nickname}]. 없는 회원은 빠져서 오고, 탈퇴 회원은 「탈퇴한 회원」으로 온다
 * - 한 번에 100명까지라 나눠 부른다
 * - 닉네임이 빈 줄은 건너뛴다
 * - 그 밖(시간 초과 · 연결 실패 · 4xx · 5xx · data 없음): 503으로 다시 시도하게 한다
 * 등록은 MemberClientConfig가 한다 (inquiry.clients.member.mode=http)
 */
@Slf4j
@RequiredArgsConstructor
public class HttpMemberNicknamesAdapter implements MemberNicknamesPort {
	static final String PATH = "/internal/v1/member/members/nicknames?ids={ids}";
	static final int MAX_IDS = 100;
	static final String UNAVAILABLE_MESSAGE = "회원 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.";
	private static final ParameterizedTypeReference<RsData<List<NicknameResponse>>> RESPONSE_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient restClient;

	@Override
	public Map<Long, String> findNicknames(Collection<Long> memberIds) {
		List<Long> ids = memberIds.stream().distinct().toList();
		Map<Long, String> nicknames = new HashMap<>();
		for (int from = 0; from < ids.size(); from += MAX_IDS) {
			List<Long> chunk = ids.subList(from, Math.min(from + MAX_IDS, ids.size()));
			for (NicknameResponse row : call(chunk)) {
				nicknames.put(row.memberId(), row.nickname());
			}
		}
		return nicknames;
	}

	private List<NicknameResponse> call(List<Long> ids) {
		String joined = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
		RsData<List<NicknameResponse>> response;
		try {
			response = restClient.get().uri(PATH, joined).retrieve().body(RESPONSE_TYPE);
		} catch (RestClientException exception) {
			log.error("회원 서비스에 닉네임을 묻지 못했습니다. ids={}", joined, exception);
			throw new InquiryExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		if (response == null || response.data() == null) {
			log.error("회원 서비스의 닉네임 응답이 비어 있습니다. ids={}, response={}", joined, response);
			throw new InquiryExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		// 한 사람 값이 비었다고 목록 전체를 막지 않는다. 그 사람은 결과에서 빠져 닉네임이 비어 보인다
		List<NicknameResponse> rows = response.data().stream()
			.filter(row -> row.memberId() != null && row.nickname() != null)
			.toList();
		if (rows.size() < response.data().size()) {
			log.warn("회원 서비스의 닉네임 응답에 빈 줄이 있어 건너뜁니다. ids={}, response={}", joined, response);
		}
		return rows;
	}

	// member MemberNicknameDto. 빈 값을 구분하려고 Long으로 받는다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record NicknameResponse(Long memberId, String nickname) {
	}
}
