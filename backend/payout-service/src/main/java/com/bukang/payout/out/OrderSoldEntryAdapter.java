package com.bukang.payout.out;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;
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
 * 결제 완료 ID를 어떤 형식으로 달라고 할지는 FORMAT_PROPERTY(IDS 기본 · BITSET · ROARING)로 고른다. 부하 테스트에서 셋을 비교한다
 * 받은 비트맵을 어떤 형식으로 읽을지는 설정이 아니라 응답의 paidFormat을 따른다 (두 서비스 설정이 어긋나도 잘못 읽지 않게)
 * 최종 정산 생성은 findSoldCourses로 묶음을 그대로 받아, 비트맵을 목록으로 풀지 않는다
 *
 * 주문 서비스가 멈추거나 오류로 응답하면 예외를 던진다. 배치는 그 대회를 실패로 남기고,
 * 다음 실행이 되돌아보기(7일)로 따라잡는다 (PRO-61). 빈 목록으로 바꾸면 그 대회 정산을 조용히 건너뛴 것처럼 보이므로 삼키지 않는다
 */
@Component
public class OrderSoldEntryAdapter implements SoldEntryPort {
	// 부하 테스트에서 형식을 바꿀 때 쓰는 설정 키 (환경 변수 PAYOUT_SOLDENTRIES_FORMAT)
	public static final String FORMAT_PROPERTY = "payout.sold-entries.format";
	private static final String PATH = "/internal/v1/order/sold-entries?raceId={raceId}";
	private static final String PATH_WITH_FORMAT = PATH + "&format={format}";
	// 새벽 배치라 사용자를 기다리게 하지 않지만, 주문 서비스가 응답하지 않을 때 배치가 끝없이 멈추지 않게 한다
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
	// 대회 하나의 참가권 전부를 한 번에 받으므로 읽기는 넉넉히 둔다
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

	private final RestClient restClient;
	private final Format format;

	@Autowired
	public OrderSoldEntryAdapter(RestClient.Builder builder,
		@Value("${payout.services.order}") String orderServiceUrl,
		@Value("${" + FORMAT_PROPERTY + ":IDS}") Format format) {
		// Boot가 주는 빌더는 주입마다 새로 만들어져(prototype) 다른 어댑터와 baseUrl이 섞이지 않는다
		this(builder
			.baseUrl(orderServiceUrl)
			.requestFactory(requestFactory())
			.build(), format);
	}

	// 테스트에서 MockRestServiceServer에 묶은 RestClient를 넣는다
	OrderSoldEntryAdapter(RestClient restClient) {
		this(restClient, Format.IDS);
	}

	OrderSoldEntryAdapter(RestClient restClient, Format format) {
		this.restClient = restClient;
		this.format = format;
	}

	// 주문 SoldEntriesFormat과 같은 이름 (서비스끼리 클래스를 나눠 쓰지 않아 따로 둔다)
	public enum Format {
		IDS, BITSET, ROARING
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
			// IDS는 주문의 기본값이라 붙이지 않는다 (format을 모르는 주문 서비스와도 붙는다)
			RestClient.RequestHeadersSpec<?> request = format == Format.IDS
				? restClient.get().uri(PATH, raceId)
				: restClient.get().uri(PATH_WITH_FORMAT, raceId, format.name());
			body = request.retrieve()
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
	// 결제 완료 ID는 paidIds(IDS)나 paidFormat · paidBase · paidBitmap(BITSET · ROARING) 중 하나로 온다
	// 주문은 ID 범위가 int를 넘는 코스를 IDS로 보내므로, 같은 응답 안에서도 코스마다 다를 수 있다
	// 비트맵을 푼 장수가 paidCount와 다르면 잘리거나 잘못 읽은 것이다. 적은 장수로 조용히 정산하지 않게 실패로 남긴다
	private record CourseResponse(Long raceCourseId, long price, List<Long> paidIds, List<Long> blockingIds,
		String paidFormat, Long paidBase, Integer paidCount, String paidBitmap) {
		SoldCourses.Course toCourse(long raceId, long sellerId, LocalDate raceDate) {
			return new SoldCourses.Course(new SoldCourses.Key(sellerId, raceId, raceDate, raceCourseId, price),
				toPaidIds(), blockingIds == null ? List.of() : blockingIds);
		}

		private PaidIds toPaidIds() {
			if (paidBitmap == null) {
				if (paidIds == null) {
					throw new IllegalArgumentException("코스의 결제 완료 ID가 없습니다. raceCourseId=" + raceCourseId);
				}
				return PaidIds.of(paidIds);
			}
			if (paidBase == null || paidFormat == null || paidCount == null) {
				throw new IllegalArgumentException("비트맵의 기준 ID · 형식 · 장수가 없습니다. raceCourseId=" + raceCourseId);
			}
			byte[] bytes = Base64.getDecoder().decode(paidBitmap);
			PaidIds ids = switch (Format.valueOf(paidFormat)) {
				case BITSET -> PaidIds.ofBitSet(paidBase, bytes);
				case ROARING -> PaidIds.ofRoaring(paidBase, bytes);
				case IDS -> throw new IllegalArgumentException("IDS 형식에 비트맵이 왔습니다. raceCourseId=" + raceCourseId);
			};
			if (ids.count() != paidCount) {
				throw new IllegalArgumentException("비트맵을 푼 장수 %d가 paidCount %d와 다릅니다. raceCourseId=%s"
					.formatted(ids.count(), paidCount, raceCourseId));
			}
			return ids;
		}
	}
}
