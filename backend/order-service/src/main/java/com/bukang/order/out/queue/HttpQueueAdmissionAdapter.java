package com.bukang.order.out.queue;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 대기열 서비스의 내부 API로 입장 중인지 묻는다 (GET /internal/v1/queue/races/{raceId}/racecourses/{raceCourseId}, PRO-226)
 * 입장 끝 시각은 대기열 시계의 admissionEndsAt 대신 남은 시간(remainingMillis)을 주문의 지금에 더해 센다
 * (두 서버의 시계·시간대가 달라도 결제 기한이 어긋나지 않는다)
 * 응답이 없거나 형식이 다르면 입장했는지 알 수 없으므로, "입장 전" 403이 아니라 503으로 다시 시도하게 한다
 * 등록은 QueueClientConfig가 한다 (order.clients.queue.mode=http)
 */
@Slf4j
@RequiredArgsConstructor
public class HttpQueueAdmissionAdapter implements QueueAdmissionPort {
	static final String PATH = "/internal/v1/queue/races/{raceId}/racecourses/{raceCourseId}";
	static final String MEMBER_ID_HEADER = "X-Member-Id";
	static final String UNAVAILABLE_MESSAGE = "대기열에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.";
	private static final ParameterizedTypeReference<RsData<Admission>> RESPONSE_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient restClient;

	@Override
	public Optional<LocalDateTime> findAdmissionEndsAt(long memberId, long raceId, long raceCourseId,
		LocalDateTime now) {
		RsData<Admission> response;
		try {
			response = restClient.get()
				.uri(PATH, raceId, raceCourseId)
				.header(MEMBER_ID_HEADER, String.valueOf(memberId))
				.retrieve()
				.body(RESPONSE_TYPE);
		} catch (HttpClientErrorException exception) {
			// 4xx는 다시 시도해도 풀리지 않는다. 경로·헤더 계약이 어긋났을 수 있어 장애와 구분해 남긴다
			log.error("대기열이 입장 확인 요청을 거절했습니다. 계약(경로·헤더)을 확인해야 합니다. status={}, memberId={}, raceCourseId={}",
				exception.getStatusCode(), memberId, raceCourseId, exception);
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		} catch (RestClientException exception) {
			log.error("대기열에 입장 확인을 묻지 못했습니다. memberId={}, raceCourseId={}", memberId, raceCourseId, exception);
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		if (response == null || response.data() == null || response.data().admitted() == null) {
			log.error("대기열 입장 확인 응답에 값이 없습니다. memberId={}, raceCourseId={}, response={}",
				memberId, raceCourseId, response);
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		Admission admission = response.data();
		if (!admission.admitted()) {
			return Optional.empty();
		}
		if (admission.remainingMillis() == null || admission.remainingMillis() <= 0) {
			// 입장 중인데 남은 시간이 없으면 응답이 어긋났다. 입장 전으로 잘못 알리지 않게 503으로 둔다
			log.error("대기열이 입장 중이라면서 남은 시간을 주지 않았습니다. memberId={}, raceCourseId={}, admission={}",
				memberId, raceCourseId, admission);
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		// DB가 마이크로초까지 저장하므로 잘라 둔다 (결제 기한으로 저장한 값과 비교가 어긋나지 않게)
		return Optional.of(now.plus(admission.remainingMillis(), ChronoUnit.MILLIS).truncatedTo(ChronoUnit.MICROS));
	}

	// 대기열 응답의 data (queue QueueAdmissionDto). 판정에 쓰는 값만 받는다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Admission(Boolean admitted, Long remainingMillis) {
	}
}
