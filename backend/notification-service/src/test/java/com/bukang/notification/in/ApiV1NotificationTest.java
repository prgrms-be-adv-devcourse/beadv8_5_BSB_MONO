package com.bukang.notification.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.member.web.MemberIdHeader;
import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.notification.app.NotificationUseCase;
import com.bukang.notification.domain.Notification;
import com.bukang.notification.out.NotificationRepository;

/**
 * 알림 요청을 받아 저장하고(같은 메시지는 하나), 내 알림만 조회·읽음 처리하는지 확인한다.
 * 표는 Flyway SQL로 만들어(ddl-auto: validate) 운영과 같은 유니크 제약으로 중복을 거른다
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiV1NotificationTest {
	private static final String URL = "/api/v1/notification/notifications";
	private static final long ME = 1L;
	private static final long OTHER = 2L;

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private NotificationKafkaListener listener;
	@Autowired
	private NotificationRepository notificationRepository;
	@Autowired
	private NotificationUseCase notificationUseCase;
	@Autowired
	private JsonConverter jsonConverter;

	@BeforeEach
	void setUp() {
		notificationRepository.deleteAll();
	}

	@Test
	@DisplayName("같은 메시지를 두 번 받아도 알림은 하나다")
	void duplicateMessage() {
		String message = message("m-1", ME, LocalDateTime.now(ZoneId.of("Asia/Seoul")));

		listener.handleRequested(message, null);
		listener.handleRequested(message, null);

		assertThat(notificationRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("message-id 헤더가 같으면 본문이 달라도 같은 메시지로 보고 하나만 저장한다")
	void duplicateByHeader() {
		LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

		listener.handleRequested(message("body-1", ME, now), "h-1");
		listener.handleRequested(message("body-2", ME, now), "h-1");

		assertThat(notificationRepository.findAll()).extracting(Notification::getMessageId).containsExactly("h-1");
	}

	@Test
	@DisplayName("같은 메시지 ID를 바로 저장하면 유니크 제약이 막아 알림은 하나다 (동시에 온 경우)")
	void uniqueConstraint() {
		LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
		NotificationRequestedEvent event = jsonConverter.fromJson(message("m-1", ME, now),
			NotificationRequestedEvent.class);
		notificationRepository.saveAndFlush(Notification.from("m-1", event));

		assertThatThrownBy(() -> notificationRepository.saveAndFlush(Notification.from("m-1", event)))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(notificationRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("깨진 메시지는 버리고 예외를 던지지 않는다")
	void brokenMessage() {
		listener.handleRequested("{not json", null);
		listener.handleRequested("{\"eventId\":\"m-9\",\"memberId\":1,\"kind\":\"UNKNOWN\"}", null);

		assertThat(notificationRepository.count()).isZero();
	}

	@Test
	@DisplayName("내 알림만 최신순으로 Notice 모양으로 준다")
	void findMine() throws Exception {
		LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
		listener.handleRequested(message("m-old", ME, now.minusDays(3)), null);
		listener.handleRequested(message("m-new", ME, now), null);
		listener.handleRequested(message("m-other", OTHER, now), null);

		mockMvc.perform(get(URL).header(MemberIdHeader.NAME, ME))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].title").value("환불이 끝났어요"))
			.andExpect(jsonPath("$.data[0].icon").value("CircleCheck"))
			.andExpect(jsonPath("$.data[0].at").value("방금"))
			.andExpect(jsonPath("$.data[0].group").value("today"))
			.andExpect(jsonPath("$.data[0].href").value("/me/deposit"))
			.andExpect(jsonPath("$.data[0].unread").value(true))
			.andExpect(jsonPath("$.data[1].group").value("earlier"));
	}

	@Test
	@DisplayName("남의 알림은 읽음 처리할 수 없다 (404), 내 알림은 읽음이 된다")
	void read() throws Exception {
		LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
		listener.handleRequested(message("m-1", ME, now), null);
		long id = notificationRepository.findAll().getFirst().getId();

		mockMvc.perform(post(URL + "/" + id + "/read").header(MemberIdHeader.NAME, OTHER))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
		assertThat(notificationRepository.findById(id).orElseThrow().isUnread()).isTrue();

		mockMvc.perform(post(URL + "/" + id + "/read").header(MemberIdHeader.NAME, ME))
			.andExpect(status().isOk());
		assertThat(notificationRepository.findById(id).orElseThrow().isUnread()).isFalse();
	}

	@Test
	@DisplayName("모두 읽음은 내 알림만 바꾼다")
	void readAll() throws Exception {
		LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
		listener.handleRequested(message("m-1", ME, now), null);
		listener.handleRequested(message("m-2", ME, now), null);
		listener.handleRequested(message("m-3", OTHER, now), null);

		mockMvc.perform(post(URL + "/read-all").header(MemberIdHeader.NAME, ME))
			.andExpect(status().isOk());

		assertThat(notificationRepository.findAll())
			.allSatisfy(n -> assertThat(n.isUnread()).isEqualTo(n.getMemberId() == OTHER));
	}

	@Test
	@DisplayName("알림 ID가 숫자가 아니면 400")
	void invalidId() throws Exception {
		mockMvc.perform(post(URL + "/abc/read").header(MemberIdHeader.NAME, ME))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다."));
	}

	@Test
	@DisplayName("회원 ID 헤더가 없으면 401")
	void noMember() throws Exception {
		mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("일이 생긴 지 7일이 지난 알림만 지운다")
	void deleteExpired() {
		LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
		listener.handleRequested(message("m-old", ME, now.minusDays(8)), null);
		listener.handleRequested(message("m-new", ME, now.minusDays(6)), null);

		assertThat(notificationUseCase.deleteExpired(7)).isEqualTo(1);
		assertThat(notificationRepository.findAll()).extracting(Notification::getMessageId).containsExactly("m-new");
	}

	private String message(String eventId, long memberId, LocalDateTime occurredAt) {
		return jsonConverter.toJson(new NotificationRequestedEvent(eventId, memberId,
			NotificationKind.REFUND_COMPLETED, "환불이 끝났어요", "가을 트레일 21K ·\n₩60,000이 예치금으로 들어왔어요.",
			"/me/deposit", occurredAt));
	}
}
