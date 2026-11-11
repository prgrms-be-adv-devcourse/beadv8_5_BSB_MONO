package com.bukang.inquiry.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.common.shared.member.web.MemberIdHeader;
import com.bukang.inquiry.domain.Inquiry;
import com.bukang.inquiry.domain.InquiryType;
import com.bukang.inquiry.out.InquiryRepository;
import com.bukang.inquiry.out.order.OrderItemSummary;
import com.bukang.inquiry.out.order.OrderSummary;

/**
 * 문의 서비스 통합 테스트의 공통 준비. 회원·상품·주문 서비스는 가짜(Stub), Kafka는 목으로 바꿔 둔다
 * 회원: 1·2는 일반 회원, 3은 승인된 판매자(판매자 문의용), 10·11은 대회 100·101의 판매자, 20은 관리자
 * 주문: 500은 회원 1의 주문(대회 100·101), 501은 회원 2의 주문(대회 100)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(StubPortsConfig.class)
public abstract class InquiryTestBase {
	protected static final String BASE = "/api/v1/inquiry";
	protected static final long ME = 1L;
	protected static final long OTHER = 2L;
	protected static final long APPROVED_SELLER = 3L;
	protected static final long SELLER = 10L;
	protected static final long OTHER_SELLER = 11L;
	protected static final long ADMIN = 20L;
	protected static final long RACE = 100L;
	protected static final long OTHER_RACE = 101L;
	protected static final long NO_RACE = 999L;
	protected static final long ORDER = 500L;
	protected static final long OTHER_ORDER = 501L;
	protected static final long ORDER_WITH_UNKNOWN_RACE = 502L;
	protected static final long NO_ORDER = 9999L;

	@Autowired
	protected MockMvc mockMvc;
	@Autowired
	protected InquiryRepository inquiryRepository;
	@Autowired
	protected StubMemberAdminPort memberAdminPort;
	@Autowired
	protected StubMemberSellerPort memberSellerPort;
	@Autowired
	protected StubMemberNicknamesPort memberNicknamesPort;
	@Autowired
	protected StubRaceSellerPort raceSellerPort;
	@Autowired
	protected StubOrderLookupPort orderLookupPort;
	@MockitoBean
	protected KafkaTemplate<String, String> kafkaTemplate;

	// NotificationKafkaSender는 커밋 뒤 공용 ForkJoin 풀에서 보낸다. 끝나지 않은 전송이 다음 테스트의 목에 늦게 잡히지 않게 기다린다
	@AfterEach
	void waitForAsyncNotifications() {
		ForkJoinPool.commonPool().awaitQuiescence(5, TimeUnit.SECONDS);
	}

	@BeforeEach
	void resetState() {
		inquiryRepository.deleteAll();
		memberAdminPort.clear();
		memberSellerPort.clear();
		memberNicknamesPort.clear();
		raceSellerPort.clear();
		orderLookupPort.clear();
		raceSellerPort.race(RACE, SELLER);
		raceSellerPort.race(OTHER_RACE, OTHER_SELLER);
		memberAdminPort.admin(ADMIN);
		memberSellerPort.seller(APPROVED_SELLER);
		memberNicknamesPort.nickname(ME, "러너하나");
		memberNicknamesPort.nickname(OTHER, "러너둘");
		memberNicknamesPort.nickname(APPROVED_SELLER, "판매자셋");
		orderLookupPort.order(ORDER, orderOf(ME, RACE, OTHER_RACE));
		orderLookupPort.order(OTHER_ORDER, orderOf(OTHER, RACE));
		doReturn(CompletableFuture.completedFuture(null)).when(kafkaTemplate).send(any(ProducerRecord.class));
	}

	// 회원 memberId의 주문. 대회마다 항목 하나씩 들어 있다
	protected static OrderSummary orderOf(long memberId, long... raceIds) {
		List<OrderItemSummary> items = java.util.Arrays.stream(raceIds)
			.mapToObj(raceId -> new OrderItemSummary(raceId, "서울 마라톤 " + raceId, "10K", "티셔츠 M, 완주 메달",
				"PREPARING"))
			.toList();
		return new OrderSummary(memberId, "OD-20261110-" + memberId, 50_000L, "PAID", items);
	}

	// 판매자 담당(대회·현장) 문의 하나를 바로 저장한다
	protected Inquiry saveSellerInquiry(long memberId, long raceId, long sellerMemberId) {
		return inquiryRepository.saveAndFlush(Inquiry.create(memberId, raceId, ORDER, InquiryType.RACE_VENUE,
			sellerMemberId, "코스가 어떻게 되나요", "코스 지도를 알려주세요."));
	}

	// 관리자 담당(신청·결제) 문의 하나를 바로 저장한다
	protected Inquiry saveAdminInquiry(long memberId, long raceId, long sellerMemberId) {
		return inquiryRepository.saveAndFlush(Inquiry.create(memberId, raceId, ORDER, InquiryType.APPLY_PAYMENT,
			sellerMemberId, "결제가 두 번 됐어요", "확인 부탁드립니다."));
	}

	// 판매자가 관리자에게 보낸 문의 하나를 바로 저장한다 (대회·주문·판매자 회원 없음)
	protected Inquiry saveSellerSupportInquiry(long memberId) {
		return inquiryRepository.saveAndFlush(Inquiry.create(memberId, null, null, InquiryType.SELLER_SUPPORT, null,
			"정산 일정이 궁금해요", "정산은 언제 되나요?"));
	}

	protected ResultActions getAs(String url, long memberId) throws Exception {
		return mockMvc.perform(get(url).header(MemberIdHeader.NAME, memberId));
	}

	protected ResultActions postAs(String url, long memberId, String json) throws Exception {
		return mockMvc.perform(post(url).header(MemberIdHeader.NAME, memberId)
			.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	protected ResultActions patchAs(String url, long memberId, String json) throws Exception {
		return mockMvc.perform(patch(url).header(MemberIdHeader.NAME, memberId)
			.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	protected ResultActions deleteAs(String url, long memberId) throws Exception {
		return mockMvc.perform(delete(url).header(MemberIdHeader.NAME, memberId));
	}

	// 주문 500(회원 1의 주문)에 묶은 문의 요청
	protected static String createJson(long raceId, String type, String title, String body) {
		return createJsonOf(raceId, ORDER, type, title, body);
	}

	// 대회·주문을 null로 보낼 수 있는 문의 요청
	protected static String createJsonOf(Long raceId, Long orderId, String type, String title, String body) {
		return """
			{"raceId": %s, "orderId": %s, "type": "%s", "title": "%s", "body": "%s"}"""
			.formatted(raceId, orderId, type, title, body);
	}

	protected static String answerJson(String content) {
		return """
			{"content": "%s"}""".formatted(content);
	}

	protected static String editJson(String title, String body) {
		return """
			{"title": "%s", "body": "%s"}""".formatted(title, body);
	}
}
