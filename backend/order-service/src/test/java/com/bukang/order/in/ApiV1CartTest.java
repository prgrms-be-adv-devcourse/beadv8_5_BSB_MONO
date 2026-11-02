package com.bukang.order.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.order.out.CartItemRepository;
import com.bukang.order.out.CartRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.support.StubRaceCoursePort;
import com.bukang.order.support.StubRaceCoursePortConfig;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(StubRaceCoursePortConfig.class)
class ApiV1CartTest {
	private static final String BASE_URL = "/api/v1/order/cart";
	private static final long MEMBER_ID = 1;
	private static final long OTHER_MEMBER_ID = 2;
	private static final long COURSE_ID = 10;
	private static final long OTHER_COURSE_ID = 11;
	private static final long CLOSED_COURSE_ID = 12;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CartRepository cartRepository;

	@Autowired
	private CartItemRepository cartItemRepository;

	@Autowired
	private ParticipationRepository participationRepository;

	@Autowired
	private StubRaceCoursePort raceCoursePort;

	@BeforeEach
	void setUp() {
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID, 100, 14, null));
		raceCoursePort.put(StubRaceCoursePort.openCourse(OTHER_COURSE_ID, 101, 14, null));
		raceCoursePort.put(StubRaceCoursePort.closedCourse(CLOSED_COURSE_ID, 102));
	}

	@AfterEach
	void tearDown() {
		cartRepository.deleteAll();
		raceCoursePort.clear();
	}

	@Test
	@DisplayName("담은 장바구니는 서버에 저장되어, 다른 요청(다른 기기)에서 조회해도 같다")
	void sameCartAcrossRequests() throws Exception {
		add(MEMBER_ID, COURSE_ID)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value(201))
			.andExpect(jsonPath("$.message").value("장바구니에 담았습니다."))
			.andExpect(jsonPath("$.data.raceCourseId").value(COURSE_ID));

		find(MEMBER_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("장바구니를 조회했습니다."))
			.andExpect(jsonPath("$.data.memberId").value(MEMBER_ID))
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].raceCourseId").value(COURSE_ID));

		// 다른 회원의 장바구니에는 보이지 않는다
		find(OTHER_MEMBER_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(0));
	}

	@Test
	@DisplayName("담은 적이 없으면 빈 장바구니를 돌려주고, 조회만으로는 장바구니를 만들지 않는다")
	void findEmptyCart() throws Exception {
		find(MEMBER_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(0));

		assertThat(cartRepository.count()).isZero();
	}

	@Test
	@DisplayName("같은 참가권을 다시 담으면 200으로 기존 항목을 돌려주고 수량은 1이다")
	void addSameCourseTwice() throws Exception {
		long itemId = addAndGetId(MEMBER_ID, COURSE_ID);

		add(MEMBER_ID, COURSE_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(200))
			.andExpect(jsonPath("$.message").value("이미 장바구니에 담긴 참가권입니다."))
			.andExpect(jsonPath("$.data.id").value(itemId));

		find(MEMBER_ID).andExpect(jsonPath("$.data.items.length()").value(1));
		assertThat(cartItemRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("담기만으로는 참가 신청·자리 확보가 생기지 않고, 신청서가 없으니 항목의 신청서 ID는 비어 있다")
	void addDoesNotHoldSeat() throws Exception {
		add(MEMBER_ID, COURSE_ID)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.participationId").doesNotExist());
		find(MEMBER_ID)
			.andExpect(jsonPath("$.data.items[0].participationId").doesNotExist())
			.andExpect(jsonPath("$.data.items[0].raceId").doesNotExist())
			.andExpect(jsonPath("$.data.items[0].participantName").doesNotExist());

		// 자리 확보는 [결제하기]에서 참가 신청을 APPLIED로 바꿀 때 일어난다 (PRO-41). 담기는 참가 신청을 만들지 않는다
		assertThat(participationRepository.count()).isZero();
	}

	@Test
	@DisplayName("담은 항목을 빼면 빈 장바구니를 돌려주고, 그 뒤에는 다른 종목을 담을 수 있다")
	void removeItem() throws Exception {
		long itemId = addAndGetId(MEMBER_ID, COURSE_ID);

		remove(MEMBER_ID, itemId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("장바구니에서 뺐습니다."))
			.andExpect(jsonPath("$.data.items.length()").value(0));

		assertThat(cartItemRepository.existsById(itemId)).isFalse();
		add(MEMBER_ID, OTHER_COURSE_ID).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("장바구니에 다른 종목이 담겨 있으면 409이고, 담긴 종목은 그대로다 (장바구니는 한 종목)")
	void addOtherCourse() throws Exception {
		add(MEMBER_ID, COURSE_ID).andExpect(status().isCreated());

		add(MEMBER_ID, OTHER_COURSE_ID)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_CART_ONE_COURSE_ONLY"))
			.andExpect(jsonPath("$.message").value("장바구니에는 한 종목만 담을 수 있습니다. 담긴 종목을 뺀 뒤 담아주세요."));
		// 같은 종목을 다시 담는 것은 그대로 된다
		add(MEMBER_ID, COURSE_ID).andExpect(status().isOk());

		find(MEMBER_ID)
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].raceCourseId").value(COURSE_ID));
	}

	@Test
	@DisplayName("장바구니가 있을 때 다른 종목을 동시에 담아도 한 종목만 남고, 늦은 쪽은 한 종목 규칙으로 409다")
	void addOtherCoursesTogether() throws Exception {
		long itemId = addAndGetId(MEMBER_ID, COURSE_ID);
		remove(MEMBER_ID, itemId).andExpect(status().isOk());

		List<String> bodies = addTogether(MEMBER_ID, COURSE_ID, OTHER_COURSE_ID);

		assertThat(bodies).extracting(body -> (Integer)JsonPath.read(body, "$.status"))
			.containsExactlyInAnyOrder(201, 409);
		// 장바구니를 잠그므로 늦은 쪽은 먼저 담긴 종목을 보고 한 종목 규칙으로 거절된다
		assertThat(bodies).filteredOn(body -> JsonPath.<Integer>read(body, "$.status") == 409)
			.extracting(body -> (String)JsonPath.read(body, "$.code"))
			.containsExactly("ORDER_CART_ONE_COURSE_ONLY");
		find(MEMBER_ID).andExpect(jsonPath("$.data.items.length()").value(1));
	}

	@Test
	@DisplayName("장바구니가 없는 회원이 다른 종목을 동시에 처음 담아도 한 종목만 남고, 늦은 쪽은 한 종목 규칙으로 409다")
	void firstAddsTogether() throws Exception {
		List<String> bodies = addTogether(MEMBER_ID, COURSE_ID, OTHER_COURSE_ID);

		assertThat(bodies).extracting(body -> (Integer)JsonPath.read(body, "$.status"))
			.containsExactlyInAnyOrder(201, 409);
		// 장바구니 만들기가 겹쳐도 이미 생긴 장바구니로 이어 가, 잠근 뒤 먼저 담긴 종목을 본다
		assertThat(bodies).filteredOn(body -> JsonPath.<Integer>read(body, "$.status") == 409)
			.extracting(body -> (String)JsonPath.read(body, "$.code"))
			.containsExactly("ORDER_CART_ONE_COURSE_ONLY");
		find(MEMBER_ID).andExpect(jsonPath("$.data.items.length()").value(1));
	}

	@Test
	@DisplayName("뺀 참가권은 다시 담을 수 있다")
	void addAgainAfterRemove() throws Exception {
		long itemId = addAndGetId(MEMBER_ID, COURSE_ID);
		remove(MEMBER_ID, itemId).andExpect(status().isOk());

		add(MEMBER_ID, COURSE_ID).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("다른 회원의 장바구니 항목은 뺄 수 없다")
	void removeOthersItem() throws Exception {
		long itemId = addAndGetId(MEMBER_ID, COURSE_ID);

		remove(OTHER_MEMBER_ID, itemId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("본인의 장바구니 항목만 뺄 수 있습니다."))
			.andExpect(jsonPath("$.code").value("ORDER_CART_ITEM_FORBIDDEN"));

		assertThat(cartItemRepository.existsById(itemId)).isTrue();
	}

	@Test
	@DisplayName("없는 장바구니 항목을 빼면 404")
	void removeMissingItem() throws Exception {
		remove(MEMBER_ID, 999)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("장바구니에 없는 항목입니다."))
			.andExpect(jsonPath("$.code").value("ORDER_CART_ITEM_NOT_FOUND"));
	}

	@Test
	@DisplayName("없는 종목은 404, 접수가 마감된 종목은 409로 담지 않는다")
	void addInvalidCourse() throws Exception {
		add(MEMBER_ID, 999)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("존재하지 않는 종목입니다."))
			.andExpect(jsonPath("$.code").value("ORDER_COURSE_NOT_FOUND"));
		add(MEMBER_ID, CLOSED_COURSE_ID)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("접수가 마감된 종목은 담을 수 없습니다."))
			.andExpect(jsonPath("$.code").value("ORDER_APPLICATION_CLOSED"));

		assertThat(cartItemRepository.count()).isZero();
	}

	@Test
	@DisplayName("종목 ID가 없으면 400")
	void addWithoutCourseId() throws Exception {
		mockMvc.perform(post(BASE_URL + "/items")
				.header("X-Member-Id", MEMBER_ID)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("담을 종목을 선택해주세요."))
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("회원 ID 헤더가 없으면 401")
	void findWithoutMemberId() throws Exception {
		mockMvc.perform(get(BASE_URL))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
	}

	// 같은 회원이 두 종목을 동시에 담고 응답 본문을 모은다
	private List<String> addTogether(long memberId, long firstCourseId, long secondCourseId) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		List<String> bodies = new ArrayList<>();
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			List<Future<String>> futures = List.of(firstCourseId, secondCourseId).stream()
				.map(courseId -> executor.submit(() -> {
					start.await();
					return add(memberId, courseId).andReturn().getResponse().getContentAsString();
				}))
				.toList();
			start.countDown();
			for (Future<String> future : futures) {
				bodies.add(future.get(30, TimeUnit.SECONDS));
			}
		}
		return bodies;
	}

	private ResultActions find(long memberId) throws Exception {
		return mockMvc.perform(get(BASE_URL).header("X-Member-Id", memberId));
	}

	private ResultActions add(long memberId, long raceCourseId) throws Exception {
		return mockMvc.perform(post(BASE_URL + "/items")
			.header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"raceCourseId\": %d}".formatted(raceCourseId)));
	}

	private ResultActions remove(long memberId, long cartItemId) throws Exception {
		return mockMvc.perform(delete(BASE_URL + "/items/" + cartItemId).header("X-Member-Id", memberId));
	}

	private long addAndGetId(long memberId, long raceCourseId) throws Exception {
		String body = add(memberId, raceCourseId)
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(body, "$.data.id").longValue();
	}
}
