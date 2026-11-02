package com.bukang.order.out.race;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.out.InternalHttpClients;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 상품 서비스(B)의 내부 API로 종목 정보를 묻는다 (GET /internal/v1/product/courses/{raceCourseId}, PRO-165)
 * 없는 종목(404)은 찾지 못한 것으로 본다. 취소된 대회는 B가 200과 saleStatus=CANCELED로 주므로 canceled로 옮긴다
 * (신청·[결제하기]는 막고, 결제한 참가권은 취소하면 전액 환불하기 위해 정보는 그대로 쓴다)
 * 응답이 없거나 형식이 다르면 판정할 수 없으므로 503으로 다시 시도하게 한다
 * 등록은 ProductClientConfig가 한다 (order.clients.product.mode=http)
 */
@Slf4j
@RequiredArgsConstructor
public class HttpRaceCourseAdapter implements RaceCoursePort {
	static final String PATH = "/internal/v1/product/courses/{raceCourseId}";
	static final String UNAVAILABLE_MESSAGE = "상품 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.";
	static final String CANCELED = "CANCELED";

	private static final ParameterizedTypeReference<RsData<CourseBody>> RESPONSE_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient restClient;

	@Override
	public Optional<RaceCourseInfo> findCourse(long raceCourseId) {
		RsData<CourseBody> response;
		try {
			response = restClient.get()
				.uri(PATH, raceCourseId)
				.retrieve()
				.body(RESPONSE_TYPE);
		} catch (HttpClientErrorException exception) {
			if (exception.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
				return Optional.empty();
			}
			// 404 말고 4xx는 다시 시도해도 풀리지 않는다. 경로·형식 계약이 어긋났을 수 있어 장애와 구분해 남긴다
			log.error("상품 서비스가 종목 정보 요청을 거절했습니다. 계약(경로)을 확인해야 합니다. status={}, raceCourseId={}, body={}",
				exception.getStatusCode(), raceCourseId,
				InternalHttpClients.abbreviate(exception.getResponseBodyAsString()));
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		} catch (RestClientException exception) {
			log.warn("상품 서비스에 종목 정보를 묻지 못했습니다. raceCourseId={}, cause={}", raceCourseId, exception.toString());
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		CourseBody body = response == null ? null : response.data();
		if (body == null || !body.isComplete()) {
			log.error("상품 서비스의 종목 정보 응답 형식이 계약과 다릅니다. raceCourseId={}, response={}", raceCourseId, response);
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		return Optional.of(body.toInfo());
	}

	// 상품 서비스 응답의 data (product RaceCourseInfoDto). 주문 RaceCourseInfo와 이름이 같은 필드와 판매 상태만 받는다
	// 티셔츠 사이즈 목록은 B enum(TshirtSize) 이름 그대로 문자열로 받는다. 없으면 티셔츠가 없는 종목이다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record CourseBody(
		Long raceCourseId,
		Long raceId,
		LocalDateTime raceDate,
		LocalDateTime applicationOpenAt,
		LocalDateTime applicationCloseAt,
		Integer minAge,
		Integer maxAge,
		Long sellerId,
		String courseName,
		Long price,
		Boolean kitIncluded,
		LocalDate refundDeadline,
		LocalDateTime scheduleChangedAt,
		String saleStatus,
		List<String> tshirtSizeOption
	) {
		// maxAge·scheduleChangedAt은 없을 수 있다(제한 없음, 바뀐 적 없음). 나머지가 비면 판정할 수 없다
		boolean isComplete() {
			return raceCourseId != null && raceId != null && raceDate != null && applicationOpenAt != null
				&& applicationCloseAt != null && minAge != null && sellerId != null && courseName != null
				&& price != null && kitIncluded != null && refundDeadline != null && saleStatus != null;
		}

		RaceCourseInfo toInfo() {
			return new RaceCourseInfo(raceCourseId, raceId, raceDate, applicationOpenAt, applicationCloseAt, minAge,
				maxAge, sellerId, courseName, price, kitIncluded, refundDeadline, scheduleChangedAt,
				CANCELED.equals(saleStatus), tshirtSizeOption);
		}
	}
}
