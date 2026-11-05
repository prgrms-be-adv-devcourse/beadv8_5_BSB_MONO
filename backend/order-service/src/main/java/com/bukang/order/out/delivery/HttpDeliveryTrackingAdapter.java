package com.bukang.order.out.delivery;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.bukang.order.domain.DeliveryTrackingStatus;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.out.InternalHttpClients;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Delivery Tracker GraphQL API로 운송장을 조회한다 (Query.track, https://tracker.delivery/docs/tracking-api, BUK-55)
 * - 인증: Authorization: TRACKQL-API-KEY <Client ID>:<Client Secret> (키는 환경 변수, 비어 있으면 부르지 않고 503)
 * - 오류는 HTTP 200이어도 본문 errors로 온다. NOT_FOUND(택배사가 아직 스캔 전)는 오류가 아니라 아직 조회되지 않음이다
 * - 그 밖의 errors(인증·BAD_REQUEST·INTERNAL), 시간 초과·연결 실패·4xx·5xx, 빈 응답: ExternalServiceUnavailableException
 * 이동 기록은 최근 50건만 받는다(오래된 것부터 온다). 시각은 한국 시각으로 바꿔 저장한다
 * 등록은 DeliveryTrackerClientConfig가 한다 (order.clients.delivery-tracker.mode=http)
 */
@Slf4j
public class HttpDeliveryTrackingAdapter implements DeliveryTrackingPort {
	static final String PATH = "/graphql";
	static final String UNAVAILABLE_MESSAGE = "배송 조회 서비스에 연결하지 못했습니다.";
	static final String NOT_FOUND_CODE = "NOT_FOUND";
	static final int EVENT_COUNT = 50;
	static final String QUERY = """
		query Track($carrierId: ID!, $trackingNumber: String!) {
			track(carrierId: $carrierId, trackingNumber: $trackingNumber) {
				lastEvent { time status { code name } }
				events(last: %d) { edges { node { time status { code name } description location { name } } } }
			}
		}
		""".formatted(EVENT_COUNT);
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	private final RestClient restClient;
	private final String authorization;

	public HttpDeliveryTrackingAdapter(RestClient restClient, String clientId, String clientSecret) {
		this.restClient = restClient;
		this.authorization = clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()
			? null : "TRACKQL-API-KEY %s:%s".formatted(clientId, clientSecret);
		if (authorization == null) {
			// 호출마다 남기면 조회 배치가 돌 때마다 배송 수만큼 쌓이므로 앱을 켤 때 한 번만 알린다
			log.error("Delivery Tracker 키(DELIVERY_TRACKER_CLIENT_ID·SECRET)가 없어 키트 배송을 조회하지 않습니다.");
		}
	}

	@Override
	public DeliveryTrackingResult track(String carrierId, String trackingNumber) {
		if (authorization == null) {
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		GraphQlResponse response;
		try {
			response = restClient.post()
				.uri(PATH)
				.header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("query", QUERY,
					"variables", Map.of("carrierId", carrierId, "trackingNumber", trackingNumber)))
				.retrieve()
				.body(GraphQlResponse.class);
		} catch (RestClientException exception) {
			// 예외 메시지에는 상대 응답 본문(입력한 운송장 번호가 되돌아올 수 있음)이 들어 있어 종류와 상태 코드만 남긴다
			log.error("Delivery Tracker에 배송을 묻지 못했습니다. carrierId={}, cause={}", carrierId,
				exception instanceof RestClientResponseException responseException
					? responseException.getStatusCode() : exception.getClass().getSimpleName());
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		if (response == null) {
			log.error("Delivery Tracker 응답이 비어 있습니다. carrierId={}", carrierId);
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		if (response.errors() != null && !response.errors().isEmpty()) {
			if (response.errors().stream().allMatch(error -> NOT_FOUND_CODE.equals(error.code()))) {
				return DeliveryTrackingResult.notTracked();
			}
			// 운송장 번호는 개인 정보와 이어질 수 있어 남기지 않는다. 오류 message가 입력값을 되돌려 줄 수 있어 코드만 남긴다
			log.error("Delivery Tracker가 오류로 답했습니다. carrierId={}, codes={}", carrierId,
				response.errors().stream().map(GraphQlError::code).toList());
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		if (response.data() == null || response.data().track() == null
			|| response.data().track().lastEvent() == null) {
			return DeliveryTrackingResult.notTracked();
		}
		return toResult(response.data().track());
	}

	private static DeliveryTrackingResult toResult(Track track) {
		List<DeliveryTrackingResult.Event> events = track.events() == null || track.events().edges() == null
			? List.of()
			: track.events().edges().stream()
				.filter(edge -> edge != null && edge.node() != null && edge.node().status() != null)
				.map(edge -> edge.node().toEvent())
				.toList();
		TrackEvent last = track.lastEvent();
		return new DeliveryTrackingResult(last.statusCode(), last.statusName(), toLocalTime(last.time()), events);
	}

	// ISO 8601(시차 포함) → 한국 시각. 읽을 수 없으면 비운다
	static LocalDateTime toLocalTime(String time) {
		if (time == null) {
			return null;
		}
		try {
			return OffsetDateTime.parse(time).atZoneSameInstant(ZONE).toLocalDateTime();
		} catch (DateTimeParseException exception) {
			log.warn("Delivery Tracker 시각을 읽지 못했습니다. time={}", time);
			return null;
		}
	}

	// 아래는 응답에서 쓰는 값만 받는다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record GraphQlResponse(Data data, List<GraphQlError> errors) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record GraphQlError(String message, Map<String, Object> extensions) {
		String code() {
			return extensions == null ? null : String.valueOf(extensions.get("code"));
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Data(Track track) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Track(TrackEvent lastEvent, Events events) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Events(List<Edge> edges) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Edge(TrackEvent node) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TrackEvent(String time, Status status, String description, Location location) {
		DeliveryTrackingStatus statusCode() {
			return status == null ? DeliveryTrackingStatus.UNKNOWN : DeliveryTrackingStatus.fromCode(status.code());
		}

		String statusName() {
			return status == null ? null : status.name();
		}

		DeliveryTrackingResult.Event toEvent() {
			return new DeliveryTrackingResult.Event(toLocalTime(time), statusCode(), statusName(), description,
				location == null ? null : location.name());
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Status(String code, String name) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Location(String name) {
	}
}
