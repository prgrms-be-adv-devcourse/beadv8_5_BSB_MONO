package com.bukang.product.support;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.product.TestcontainersConfiguration;
import com.bukang.product.app.ProductFacade;
import com.bukang.product.domain.ProductMember;
import com.bukang.product.domain.ProductOutboxEvent;
import com.bukang.product.domain.ProductSeller;
import com.bukang.product.domain.document.RaceCourseDocument;
import com.bukang.product.dto.RaceSaveRequestDto;
import com.bukang.product.out.ParticipationHoldRepository;
import com.bukang.product.out.ProductMemberRepository;
import com.bukang.product.out.ProductOutboxEventRepository;
import com.bukang.product.out.ProductSellerRepository;
import com.bukang.product.out.RaceCourseDocumentRepository;
import com.bukang.product.out.RaceCourseRepository;
import com.bukang.product.out.RaceRepository;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * product-service 통합 테스트의 공통 설정. 모든 테스트 클래스가 이 클래스를 상속해 같은 설정을 쓰므로,
 * Spring 컨텍스트와 ES 컨테이너(nori)를 한 번만 띄워 함께 쓴다.
 * DB는 H2(test 프로파일)이고, ES 컨테이너가 필요해 Docker가 없으면 건너뛴다.
 * Kafka 메시지는 아웃박스 테이블에 남은 행으로 확인한다 (테스트에서는 릴레이 스케줄러가 돌지 않아 보내지 않는다).
 * 릴레이를 직접 부르는 테스트를 위해 KafkaTemplate은 브로커 없이 바로 성공한 것으로 돌려준다.
 * 테스트마다 승인된 판매자 2명(회원 9001 → 판매자 7001, 회원 9002 → 판매자 7002)을 넣고, 끝나면 DB와 검색 문서를 비운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
public abstract class ProductIntegrationTest {
	protected static final int SELLER_MEMBER_ID = 9001;
	protected static final int SELLER_ID = 7001;
	protected static final int OTHER_SELLER_MEMBER_ID = 9002;
	protected static final int OTHER_SELLER_ID = 7002;
	// 판매자가 아닌 회원 (복제본도 없다)
	protected static final int NOT_SELLER_MEMBER_ID = 9999;

	@Autowired
	protected MockMvc mockMvc;
	@Autowired
	protected JsonConverter jsonConverter;
	@Autowired
	protected TransactionTemplate transactionTemplate;
	@Autowired
	protected ProductFacade productFacade;
	@Autowired
	protected ProductMemberRepository productMemberRepository;
	@Autowired
	protected ProductSellerRepository productSellerRepository;
	@Autowired
	protected RaceRepository raceRepository;
	@Autowired
	protected RaceCourseRepository raceCourseRepository;
	@Autowired
	protected ParticipationHoldRepository participationHoldRepository;
	@Autowired
	protected RaceCourseDocumentRepository raceCourseDocumentRepository;
	@Autowired
	protected ProductOutboxEventRepository productOutboxEventRepository;
	@PersistenceContext
	private EntityManager entityManager;

	// 실제 KafkaTemplate을 감싼다. 자동 설정 빈의 타입이 KafkaTemplate<?, ?>라 같은 타입으로 받는다
	@MockitoSpyBean
	private KafkaTemplate<?, ?> kafkaTemplate;

	// 등록 결과: 대회 ID와 요청 순서대로의 종목 ID
	protected record RegisteredRace(int raceId, List<Integer> courseIds) {
		public int courseId() {
			return courseIds.getFirst();
		}
	}

	@BeforeEach
	void setUpProductTest() {
		// 브로커 없이 보낸 것으로 처리한다 (보내지 않으면 send()가 브로커를 기다리다 max.block.ms 뒤 실패한다)
		doReturn(CompletableFuture.completedFuture(null)).when(kafka()).send(anyString(), anyString(), anyString());
		seedSeller(SELLER_MEMBER_ID, SELLER_ID);
		seedSeller(OTHER_SELLER_MEMBER_ID, OTHER_SELLER_ID);
	}

	// FK 순서대로 지운다: 자리 → 대회(종목은 함께 지워진다) → 판매자 → 회원, 그리고 아웃박스와 검색 문서
	@AfterEach
	void cleanUpProductTest() {
		participationHoldRepository.deleteAll();
		raceRepository.deleteAll();
		productSellerRepository.deleteAll();
		productMemberRepository.deleteAll();
		productOutboxEventRepository.deleteAll();
		raceCourseDocumentRepository.deleteAll();
	}

	@SuppressWarnings("unchecked")
	protected KafkaTemplate<String, String> kafka() {
		return (KafkaTemplate<String, String>)kafkaTemplate;
	}

	// 이 테스트에서 아웃박스에 남긴(보낼) 토픽의 메시지 본문 (남긴 순서대로)
	protected List<String> outboxPayloads(String topic) {
		return outboxEvents(topic).stream().map(ProductOutboxEvent::getPayload).toList();
	}

	// 이 테스트에서 아웃박스에 남긴(보낼) 토픽의 메시지 key = 대상 ID (남긴 순서대로)
	protected List<String> outboxKeys(String topic) {
		return outboxEvents(topic).stream().map(ProductOutboxEvent::getMessageKey).toList();
	}

	protected List<ProductOutboxEvent> outboxEvents(String topic) {
		return entityManager
			.createQuery("select e from ProductOutboxEvent e where e.eventType = :topic order by e.id",
				ProductOutboxEvent.class)
			.setParameter("topic", topic)
			.getResultList();
	}

	protected void seedSeller(int memberId, int sellerId) {
		LocalDateTime now = LocalDateTime.now();
		ProductMember member = productMemberRepository.save(new ProductMember(memberId, now, now, "러너" + memberId));
		productSellerRepository.save(new ProductSeller(sellerId, member, SellerType.BUSINESS, "(주)러닝" + sellerId,
			"123-45-67890", "김대표", SellerStatus.APPROVED, now, now));
	}

	// ---------- 대회 등록 ----------

	// 판매 시작일이 오늘로부터 이틀 뒤 이후인 대회를 등록한다 (판매 예정)
	protected RegisteredRace register(RaceSaveRequestDto request) throws Exception {
		return register(SELLER_MEMBER_ID, request);
	}

	protected RegisteredRace register(int memberId, RaceSaveRequestDto request) throws Exception {
		MvcResult result = postRace(memberId, request)
			.andExpect(status().isCreated())
			.andReturn();
		String body = result.getResponse().getContentAsString();
		int raceId = JsonPath.read(body, "$.data.raceId");
		List<Integer> courseIds = JsonPath.read(body, "$.data.courseIds");
		return new RegisteredRace(raceId, courseIds);
	}

	// 판매 시작일이 오늘이거나 지난 대회를 판매 중으로 만든다 (기본 요청이면 개인 예매가 한 시간 전에 열린 대회)
	// 판매 시작일은 오늘로부터 이틀 뒤부터만 등록할 수 있으므로(400), 일정을 모두 같은 날수만큼 미래로 옮겨 API로 등록한 뒤
	// DB의 일정을 요청 값으로 되돌리고, 상태 배치처럼 판매 중으로 바꿔 검색 문서를 다시 색인한다
	// 판매 시작일이 내일이면 같은 방법으로 등록하고 판매 예정으로 둔다
	// 대회일이 지났어도 판매 종료로는 바꾸지 않는다 (상태 배치가 아직 돌지 않은 대회)
	// 판매 시작일이 이틀 뒤 이후인 요청이면 그대로 등록해 판매 예정이 된다
	protected RegisteredRace registerOnSale(RaceSaveRequestDto request) throws Exception {
		LocalDate today = LocalDate.now();
		LocalDate saleOpenDate = saleOpenDate(request);
		if (!saleOpenDate.isBefore(today.plusDays(2))) {
			return register(request);
		}
		// 날짜 단위로 옮겨야 '크루 마감 다음 날 06:00 이후', '환불 기준일 ≤ 대회 날짜' 같은 검사 결과가 그대로다
		long days = ChronoUnit.DAYS.between(saleOpenDate, today) + 2;
		RaceSaleStatus saleStatus = saleOpenDate.isAfter(today) ? RaceSaleStatus.SCHEDULED : RaceSaleStatus.ON_SALE;
		RegisteredRace race = register(shiftSchedule(request, days));
		transactionTemplate.executeWithoutResult(status -> entityManager.createQuery("""
				update Race r set r.raceDate = :raceDate, r.saleOpenDate = :saleOpenDate,
					r.crewSaleStartAt = :crewSaleStartAt, r.crewSaleEndAt = :crewSaleEndAt,
					r.individualOpenAt = :individualOpenAt, r.applicationCloseAt = :applicationCloseAt,
					r.refundDeadline = :refundDeadline, r.saleStatus = :saleStatus
				where r.id = :id""")
			.setParameter("raceDate", request.getRaceDate())
			.setParameter("saleOpenDate", saleOpenDate)
			.setParameter("crewSaleStartAt", request.getCrewSaleStartAt())
			.setParameter("crewSaleEndAt", request.getCrewSaleEndAt())
			.setParameter("individualOpenAt", request.getIndividualOpenAt())
			.setParameter("applicationCloseAt", request.getApplicationCloseAt())
			.setParameter("refundDeadline", request.getRefundDeadline())
			.setParameter("saleStatus", saleStatus)
			.setParameter("id", race.raceId())
			.executeUpdate());
		List<RaceCourseDocument> documents = transactionTemplate.execute(status ->
			raceRepository.findById(race.raceId()).orElseThrow().getCourses().stream()
				.map(RaceCourseDocument::from)
				.toList());
		raceCourseDocumentRepository.saveAll(documents);
		return race;
	}

	// 판매 시작일: 크루 예매 시작·개인 예매 오픈 중 이른 날짜 (Race와 같은 계산)
	private static LocalDate saleOpenDate(RaceSaveRequestDto request) {
		LocalDateTime crewSaleStartAt = request.getCrewSaleStartAt();
		LocalDateTime individualOpenAt = request.getIndividualOpenAt();
		if (crewSaleStartAt != null && crewSaleStartAt.isBefore(individualOpenAt)) {
			return crewSaleStartAt.toLocalDate();
		}
		return individualOpenAt.toLocalDate();
	}

	// 대회 일정(대회 일시, 크루 예매, 개인 예매 오픈, 접수 마감, 환불 기준일)을 모두 days일 뒤로 옮긴 요청
	private static RaceSaveRequestDto shiftSchedule(RaceSaveRequestDto request, long days) {
		return new RaceSaveRequestDto(request.getTitle(), request.getDescription(), request.getLocation(),
			request.getRegionSido(), request.getRegionSigungu(), request.getRaceDate().plusDays(days),
			plusDays(request.getCrewSaleStartAt(), days), plusDays(request.getCrewSaleEndAt(), days),
			request.getIndividualOpenAt().plusDays(days), request.getApplicationCloseAt().plusDays(days),
			request.getRefundDeadline().plusDays(days), request.getRefundPolicy(), request.getCrewMinCount(),
			request.getCrewMaxCount(), request.getCourses());
	}

	private static LocalDateTime plusDays(LocalDateTime dateTime, long days) {
		return dateTime == null ? null : dateTime.plusDays(days);
	}

	// ---------- 자리 (주문 서비스가 부르는 내부 API) ----------

	protected ResultActions hold(int raceCourseId, int participationId, LocalDateTime expiresAt) throws Exception {
		String body = "{\"raceCourseId\":%d,\"participationId\":%d,\"expiresAt\":\"%s\"}"
			.formatted(raceCourseId, participationId, expiresAt.withNano(0));
		return mockMvc.perform(post("/internal/v1/product/holds")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	protected ResultActions hold(int raceCourseId, int participationId) throws Exception {
		return hold(raceCourseId, participationId, LocalDateTime.now().plusMinutes(10));
	}

	protected ResultActions sell(int participationId) throws Exception {
		return mockMvc.perform(post("/internal/v1/product/holds/" + participationId + "/sell"));
	}

	protected ResultActions restore(int participationId) throws Exception {
		return mockMvc.perform(post("/internal/v1/product/holds/" + participationId + "/restore"));
	}

	// ---------- 요청 ----------

	// memberId가 null이면 X-Member-Id 헤더를 보내지 않는다
	protected ResultActions postJson(String path, Integer memberId, Object body) throws Exception {
		return mockMvc.perform(withMember(post(path), memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content(jsonConverter.toJson(body)));
	}

	// 대회 등록 요청. 등록 중복 방지 키(Idempotency-Key)는 요청마다 새로 만든다
	protected ResultActions postRace(Integer memberId, Object body) throws Exception {
		return mockMvc.perform(withMember(post("/api/v1/product/races"), memberId)
			.header("Idempotency-Key", newIdempotencyKey())
			.contentType(MediaType.APPLICATION_JSON)
			.content(jsonConverter.toJson(body)));
	}

	protected static String newIdempotencyKey() {
		return UUID.randomUUID().toString();
	}

	protected ResultActions putJson(String path, Integer memberId, Object body) throws Exception {
		return mockMvc.perform(withMember(put(path), memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content(jsonConverter.toJson(body)));
	}

	protected ResultActions postAs(String path, Integer memberId) throws Exception {
		return mockMvc.perform(withMember(post(path), memberId));
	}

	protected ResultActions getAs(String path, Integer memberId) throws Exception {
		return mockMvc.perform(withMember(get(path), memberId));
	}

	private MockHttpServletRequestBuilder withMember(MockHttpServletRequestBuilder request, Integer memberId) {
		return memberId == null ? request : request.header("X-Member-Id", memberId);
	}

	// ---------- 동시 요청 ----------

	public interface Call<T> {
		T call(int index) throws Exception;
	}

	// count개 요청을 스레드마다 하나씩 띄워 한꺼번에 출발시키고, 결과를 요청 순서대로 돌려준다
	protected <T> List<T> runConcurrently(int count, Call<T> call) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(count);
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<T>> futures = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				int index = i;
				futures.add(pool.submit(() -> {
					start.await();
					return call.call(index);
				}));
			}
			start.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) {
				results.add(future.get());
			}
			return results;
		} finally {
			pool.shutdownNow();
		}
	}
}
