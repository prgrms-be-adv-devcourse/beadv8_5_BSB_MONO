package com.bukang.payout.out;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.bukang.common.global.rsdata.RsData;

/**
 * 상품 서비스의 정산 대상 대회 내부 API를 HTTP로 부르는 어댑터 (PRO-224, 상품 API는 PRO-230)
 * - 1차: GET {상품 서비스}/internal/v1/product/races/by-refund-deadline?from=&to=
 * - 최종: GET {상품 서비스}/internal/v1/product/races/by-race-date?from=&to=
 * RsData.data 배열을 RaceSchedule로 바꾼다. 상품 서비스는 취소된 대회를 돌려주지 않는다
 *
 * 테스트 프로파일에서는 FakeRaceAdapter가 대신 뜬다. dev(로컬)와 prod는 이 어댑터로 실제 상품 서비스를 부른다
 * 상품 서비스가 멈추거나 오류로 응답하면 예외를 던진다. 배치는 그날 실행을 실패로 남기고(스케줄러가 로그를 남김),
 * 다음 실행이 되돌아보기(7일)로 따라잡는다 (PRO-61). 빈 목록으로 바꾸면 그날 정산을 조용히 건너뛴 것처럼 보이므로 삼키지 않는다
 */
@Component
@Profile("!test")
public class ProductRaceAdapter implements RacePort {
	private static final String BY_REFUND_DEADLINE_PATH =
		"/internal/v1/product/races/by-refund-deadline?from={from}&to={to}";
	private static final String BY_RACE_DATE_PATH = "/internal/v1/product/races/by-race-date?from={from}&to={to}";
	// 새벽 배치라 사용자를 기다리게 하지 않지만, 상품 서비스가 응답하지 않을 때 배치가 끝없이 멈추지 않게 한다
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

	private final RestClient restClient;

	@Autowired
	public ProductRaceAdapter(@Value("${payout.services.product}") String productServiceUrl) {
		this(RestClient.builder()
			.baseUrl(productServiceUrl)
			.requestFactory(requestFactory())
			.build());
	}

	// 테스트에서 MockRestServiceServer에 묶은 RestClient를 넣는다
	ProductRaceAdapter(RestClient restClient) {
		this.restClient = restClient;
	}

	@Override
	public List<RaceSchedule> findRacesByRefundDeadline(LocalDate fromRefundDeadline, LocalDate toRefundDeadline) {
		return find(BY_REFUND_DEADLINE_PATH, fromRefundDeadline, toRefundDeadline);
	}

	@Override
	public List<RaceSchedule> findRacesByRaceDate(LocalDate fromRaceDate, LocalDate toRaceDate) {
		return find(BY_RACE_DATE_PATH, fromRaceDate, toRaceDate);
	}

	private List<RaceSchedule> find(String path, LocalDate from, LocalDate to) {
		RsData<List<RaceScheduleResponse>> body = restClient.get()
			.uri(path, from, to)
			.retrieve()
			.body(new ParameterizedTypeReference<>() {
			});

		if (body == null || body.data() == null) {
			throw new IllegalStateException("상품 서비스의 정산 대상 대회 응답이 비어 있습니다. %s ~ %s".formatted(from, to));
		}
		return body.data().stream()
			.map(RaceScheduleResponse::toRaceSchedule)
			.toList();
	}

	private static SimpleClientHttpRequestFactory requestFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(CONNECT_TIMEOUT);
		factory.setReadTimeout(READ_TIMEOUT);
		return factory;
	}

	// 상품 서비스 응답(RaceScheduleDto)의 모양. 포트의 RaceSchedule이 응답 형식에 묶이지 않도록 따로 둔다
	// 상품 서비스는 대회 일시(LocalDateTime)를 주고, 정산은 날짜만 쓴다
	private record RaceScheduleResponse(int raceId, String raceName, LocalDateTime raceDate,
		LocalDate refundDeadline) {
		RaceSchedule toRaceSchedule() {
			return new RaceSchedule(raceId, raceName, raceDate.toLocalDate(), refundDeadline);
		}
	}
}
