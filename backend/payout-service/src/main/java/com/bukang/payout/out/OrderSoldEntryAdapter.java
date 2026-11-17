package com.bukang.payout.out;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.payout.domain.exception.PayoutOrderLookupUnavailableException;

/**
 * 주문 서비스의 정산용 판매 내역 내부 API를 HTTP로 부르는 어댑터 (PRO-62)
 * GET {주문 서비스}/internal/v1/order/sold-entries?raceId= → RsData.data의 코스 묶음을 SoldCourses로 바꾼다
 * 응답은 참가권마다 칸을 반복하지 않고 대회 · 코스로 묶어 줄였다 (BUK-244)
 * 최종 정산 생성은 findSoldCourses로 묶음을 그대로 받아 참가권 목록으로 풀지 않는다
 *
 * 주문 서비스가 멈추거나 오류로 응답하면 예외를 던진다. 배치는 그 대회를 실패로 남기고,
 * 다음 실행이 되돌아보기(7일)로 따라잡는다 (PRO-61). 빈 목록으로 바꾸면 그 대회 정산을 조용히 건너뛴 것처럼 보이므로 삼키지 않는다
 */
@Component
public class OrderSoldEntryAdapter implements SoldEntryPort {
	private static final String PATH = "/internal/v1/order/sold-entries?raceId={raceId}";
	// 새벽 배치라 사용자를 기다리게 하지 않지만, 주문 서비스가 응답하지 않을 때 배치가 끝없이 멈추지 않게 한다
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
	// 대회 하나의 참가권 전부를 한 번에 받으므로 읽기는 넉넉히 둔다
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

	private final RestClient restClient;

	@Autowired
	public OrderSoldEntryAdapter(RestClient.Builder builder,
		@Value("${payout.services.order}") String orderServiceUrl) {
		// Boot가 주는 빌더는 주입마다 새로 만들어져(prototype) 다른 어댑터와 baseUrl이 섞이지 않는다
		this(builder
			.baseUrl(orderServiceUrl)
			.requestFactory(requestFactory())
			.build());
	}

	// 테스트에서 MockRestServiceServer에 묶은 RestClient를 넣는다
	OrderSoldEntryAdapter(RestClient restClient) {
		this.restClient = restClient;
	}

	// 대사는 참가권 목록으로 비교하므로 묶음을 풀어 준다
	@Override
	public List<SoldEntry> findSoldEntries(long raceId) {
		return findSoldCourses(raceId).toEntriesSortedById();
	}

	@Override
	public SoldCourses findSoldCourses(long raceId) {
		RsData<SoldEntriesResponse> body;
		try {
			body = restClient.get()
				.uri(PATH, raceId)
				.retrieve()
				.body(new ParameterizedTypeReference<>() {
				});
		} catch (RestClientException exception) {
			throw new PayoutOrderLookupUnavailableException(raceId, exception);
		}

		if (body == null || body.data() == null || body.data().courses() == null) {
			throw new PayoutOrderLookupUnavailableException(raceId,
				new IllegalStateException("주문 서비스의 판매 내역 응답이 비어 있습니다."));
		}
		SoldEntriesResponse data = body.data();
		if (!data.courses().isEmpty() && (data.sellerId() == null || data.raceDate() == null)) {
			throw new PayoutOrderLookupUnavailableException(raceId,
				new IllegalStateException("주문 서비스의 판매 내역에 판매자나 대회일이 없습니다."));
		}
		try {
			return new SoldCourses(data.courses().stream()
				.map(course -> course.toCourse(raceId, data.sellerId(), data.raceDate()))
				.toList());
		} catch (IllegalArgumentException exception) {
			throw new PayoutOrderLookupUnavailableException(raceId, exception);
		}
	}

	private static SimpleClientHttpRequestFactory requestFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(CONNECT_TIMEOUT);
		factory.setReadTimeout(READ_TIMEOUT);
		return factory;
	}

	// 주문 서비스 응답(SoldEntriesDto)의 모양. 포트의 SoldEntry가 응답 형식에 묶이지 않도록 따로 둔다
	// 판매가 없으면 courses는 빈 배열이고 sellerId · raceDate는 null이다
	private record SoldEntriesResponse(LocalDate raceDate, Long sellerId, List<CourseResponse> courses) {
	}

	// blockingIds는 결제 완료가 아닌 판매(지금은 취소 중뿐)다. 있으면 그 판매자 × 대회의 최종 정산을 미룬다 (BUK-52)
	private record CourseResponse(Long raceCourseId, long price, List<Long> paidIds, List<Long> blockingIds) {
		SoldCourses.Course toCourse(long raceId, long sellerId, LocalDate raceDate) {
			if (paidIds == null) {
				throw new IllegalArgumentException("코스의 결제 완료 ID가 없습니다. raceCourseId=" + raceCourseId);
			}
			return new SoldCourses.Course(new SoldCourses.Key(sellerId, raceId, raceDate, raceCourseId, price),
				PaidIds.of(paidIds), blockingIds == null ? List.of() : blockingIds);
		}
	}
}
