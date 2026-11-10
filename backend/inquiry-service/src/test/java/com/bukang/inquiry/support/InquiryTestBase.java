package com.bukang.inquiry.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
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

/**
 * 문의 서비스 통합 테스트의 공통 준비. 회원·상품 서비스는 가짜(Stub), Kafka는 목으로 바꿔 둔다
 * 회원: 1·2는 일반 회원, 10·11은 판매자(대회 100·101의 판매자), 20은 관리자
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(StubPortsConfig.class)
public abstract class InquiryTestBase {
	protected static final String BASE = "/api/v1/inquiry";
	protected static final long ME = 1L;
	protected static final long OTHER = 2L;
	protected static final long SELLER = 10L;
	protected static final long OTHER_SELLER = 11L;
	protected static final long ADMIN = 20L;
	protected static final long RACE = 100L;
	protected static final long OTHER_RACE = 101L;
	protected static final long NO_RACE = 999L;

	@Autowired
	protected MockMvc mockMvc;
	@Autowired
	protected InquiryRepository inquiryRepository;
	@Autowired
	protected StubMemberAdminPort memberAdminPort;
	@Autowired
	protected StubRaceSellerPort raceSellerPort;
	@MockitoBean
	protected KafkaTemplate<String, String> kafkaTemplate;

	@BeforeEach
	void resetState() {
		inquiryRepository.deleteAll();
		memberAdminPort.clear();
		raceSellerPort.clear();
		raceSellerPort.race(RACE, SELLER);
		raceSellerPort.race(OTHER_RACE, OTHER_SELLER);
		memberAdminPort.admin(ADMIN);
		doReturn(CompletableFuture.completedFuture(null)).when(kafkaTemplate).send(any(ProducerRecord.class));
	}

	// 판매자 담당(대회·현장) 문의 하나를 바로 저장한다
	protected Inquiry saveSellerInquiry(long memberId, long raceId, long sellerMemberId) {
		return inquiryRepository.saveAndFlush(Inquiry.create(memberId, raceId, InquiryType.RACE_VENUE,
			sellerMemberId, "코스가 어떻게 되나요", "코스 지도를 알려주세요."));
	}

	// 관리자 담당(신청·결제) 문의 하나를 바로 저장한다
	protected Inquiry saveAdminInquiry(long memberId, long raceId, long sellerMemberId) {
		return inquiryRepository.saveAndFlush(Inquiry.create(memberId, raceId, InquiryType.APPLY_PAYMENT,
			sellerMemberId, "결제가 두 번 됐어요", "확인 부탁드립니다."));
	}

	protected ResultActions getAs(String url, long memberId) throws Exception {
		return mockMvc.perform(get(url).header(MemberIdHeader.NAME, memberId));
	}

	protected ResultActions postAs(String url, long memberId, String json) throws Exception {
		return mockMvc.perform(post(url).header(MemberIdHeader.NAME, memberId)
			.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	protected static String createJson(long raceId, String type, String title, String body) {
		return """
			{"raceId": %d, "type": "%s", "title": "%s", "body": "%s"}""".formatted(raceId, type, title, body);
	}

	protected static String answerJson(String content) {
		return """
			{"content": "%s"}""".formatted(content);
	}
}
