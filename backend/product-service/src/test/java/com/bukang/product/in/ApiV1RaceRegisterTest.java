package com.bukang.product.in;

import static com.bukang.product.support.RaceRequests.course;
import static com.bukang.product.support.RaceRequests.image;
import static com.bukang.product.support.RaceRequests.race;
import static com.bukang.product.support.RaceRequests.thumbnail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.common.shared.file.domain.FileType;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.product.domain.CourseStatus;
import com.bukang.product.domain.CourseSurface;
import com.bukang.product.domain.CourseType;
import com.bukang.product.domain.ProductMember;
import com.bukang.product.domain.ProductSeller;
import com.bukang.product.domain.Race;
import com.bukang.product.domain.RaceCourse;
import com.bukang.product.domain.TshirtSize;
import com.bukang.product.domain.document.RaceCourseDocument;
import com.bukang.product.dto.RaceCourseSaveRequestDto;
import com.bukang.product.dto.RaceImageRequestDto;
import com.bukang.product.dto.RaceSaveRequestDto;
import com.bukang.product.support.ProductIntegrationTest;
import com.bukang.product.support.RaceRequests.CourseBuilder;
import com.bukang.product.support.RaceRequests.RaceBuilder;
import com.jayway.jsonpath.JsonPath;

/**
 * 대회 등록 API(POST /api/v1/product/races)를 확인한다.
 * 성공하면 대회·종목을 판매 예정(SCHEDULED)으로 저장하고, 판매 시작일을 크루 예매 시작·개인 예매 오픈 중 이른 날짜로 채운다.
 * 판매 시작일은 오늘로부터 이틀 뒤부터 정할 수 있다 (등록한 뒤 하루 이상 판매 예정으로 둔다).
 * 커밋한 뒤에는 종목마다 검색 문서를 만들고 race.filelink.requested로 종목별 이미지 연결을 요청한다.
 * 실패 쪽은 인증(401·403), 요청 DTO의 검증 애노테이션, RaceRequestValidator의 규칙(값끼리 맞추는 검사), 요청 본문 형식 오류를
 * 하나씩 보고, 거절된 요청은 대회·종목을 저장하지 않고 파일 연결 메시지도 보내지 않는지 함께 확인한다.
 */
class ApiV1RaceRegisterTest extends ProductIntegrationTest {
	private static final String RACES_PATH = "/api/v1/product/races";
	// 응답 메시지에 보여 주는 날짜·일시 형식 (ApiV1RaceController, Race와 같다)
	private static final DateTimeFormatter MESSAGE_DATE = DateTimeFormatter.ofPattern("M월 d일");
	private static final DateTimeFormatter MESSAGE_DATE_TIME = DateTimeFormatter.ofPattern("M월 d일 HH:mm");
	// 판매 시작일, 예매가 열리는 일시 순서
	private static final String REGISTER_MESSAGE = "대회를 등록했습니다. %s 0시부터 판매 중으로 표시되고, %s에 열립니다.";
	// 오늘로부터 이틀 뒤 날짜
	private static final String SALE_OPEN_TOO_SOON_MESSAGE =
		"판매 시작일은 오늘로부터 이틀 뒤(%s)부터 정할 수 있습니다. 크루 예매 시작·개인 예매 오픈 일시를 확인해주세요.";
	// 판매자 신청이 승인 대기인 회원 (테스트 안에서 직접 넣는다)
	private static final int PENDING_SELLER_MEMBER_ID = 9003;
	private static final int PENDING_SELLER_ID = 7003;

	// ---------- 등록 성공 ----------

	@Test
	@DisplayName("크루 예매 없이 이틀 뒤에 열리는 개인 예매로 등록하면 201이고, 판매 예정이며 판매 시작일은 개인 예매 오픈 날짜다. "
		+ "메시지로 판매 중 표시일과 개인 예매 오픈 일시를 알린다")
	void registerWithoutCrewSale() throws Exception {
		RaceSaveRequestDto request = upcomingRace().build();
		LocalDateTime individualOpenAt = request.getIndividualOpenAt();

		MvcResult result = postRace(SELLER_MEMBER_ID, request)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value(201))
			// 예: "대회를 등록했습니다. 10월 16일 0시부터 판매 중으로 표시되고, 개인 예매는 10월 16일 14:30에 열립니다."
			.andExpect(jsonPath("$.message").value(REGISTER_MESSAGE.formatted(
				individualOpenAt.format(MESSAGE_DATE), "개인 예매는 " + individualOpenAt.format(MESSAGE_DATE_TIME))))
			.andExpect(jsonPath("$.data.raceId").isNumber())
			.andExpect(jsonPath("$.data.saleStatus").value("SCHEDULED"))
			.andExpect(jsonPath("$.data.courseIds.length()").value(1))
			.andReturn();

		String body = result.getResponse().getContentAsString();
		int raceId = JsonPath.read(body, "$.data.raceId");
		int courseId = JsonPath.read(body, "$.data.courseIds[0]");
		String saleOpenDate = JsonPath.read(body, "$.data.saleOpenDate");
		assertThat(LocalDate.parse(saleOpenDate)).isEqualTo(individualOpenAt.toLocalDate());
		assertThat(result.getResponse().getHeader("Location")).isEqualTo(RACES_PATH + "/" + raceId);
		assertThat(raceRepository.findById(raceId)).isPresent();
		assertThat(raceCourseRepository.findById(courseId)).isPresent();
	}

	@Test
	@DisplayName("크루 예매가 이틀 뒤 시작하고 개인 예매가 크루 마감 다음 날 10:00에 열리면, 판매 시작일은 더 이른 크루 예매 시작 날짜이고 "
		+ "메시지로 크루 예매와 개인 예매가 열리는 일시를 함께 알린다")
	void registerWithCrewSale() throws Exception {
		RaceBuilder builder = upcomingRace();
		LocalDateTime crewSaleStartAt = builder.now().plusDays(2);
		LocalDateTime crewSaleEndAt = builder.now().plusDays(3);
		LocalDateTime individualOpenAt = crewSaleEndAt.toLocalDate().plusDays(1).atTime(10, 0);
		RaceSaveRequestDto request = builder.crewSale(crewSaleStartAt, crewSaleEndAt)
			.individualOpenAt(individualOpenAt)
			.crewCount(5, 20)
			.course(course().quota(30, 70))
			.build();

		MvcResult result = postRace(SELLER_MEMBER_ID, request)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.message").value(REGISTER_MESSAGE.formatted(crewSaleStartAt.format(MESSAGE_DATE),
				"크루 예매는 %s, 개인 예매는 %s".formatted(crewSaleStartAt.format(MESSAGE_DATE_TIME),
					individualOpenAt.format(MESSAGE_DATE_TIME)))))
			.andExpect(jsonPath("$.data.saleStatus").value("SCHEDULED"))
			.andReturn();

		String body = result.getResponse().getContentAsString();
		String saleOpenDate = JsonPath.read(body, "$.data.saleOpenDate");
		assertThat(LocalDate.parse(saleOpenDate)).isEqualTo(crewSaleStartAt.toLocalDate());

		int raceId = JsonPath.read(body, "$.data.raceId");
		int courseId = JsonPath.read(body, "$.data.courseIds[0]");
		Race savedRace = raceRepository.findById(raceId).orElseThrow();
		assertThat(savedRace.getSaleOpenDate()).isEqualTo(crewSaleStartAt.toLocalDate());
		assertThat(savedRace.getCrewSaleStartAt()).isEqualTo(crewSaleStartAt);
		assertThat(savedRace.getCrewSaleEndAt()).isEqualTo(crewSaleEndAt);
		assertThat(savedRace.getIndividualOpenAt()).isEqualTo(individualOpenAt);
		assertThat(savedRace.getCrewMaxCount()).isEqualTo(20);
		RaceCourse savedCourse = raceCourseRepository.findById(courseId).orElseThrow();
		assertThat(savedCourse.getCrewQuota()).isEqualTo(30);
		assertThat(savedCourse.getIndividualQuota()).isEqualTo(70);
	}

	@Test
	@DisplayName("부산 대회에 10K·하프 두 종목을 등록하면 대회·종목 값이 요청대로 저장되고, 종목 ID는 요청 순서대로 돌려준다")
	void saveRaceAndCourses() throws Exception {
		RaceBuilder builder = upcomingRace();
		LocalDateTime raceDate = builder.now().plusDays(20);
		LocalDateTime applicationCloseAt = builder.now().plusDays(12);
		LocalDate refundDeadline = raceDate.toLocalDate().minusDays(3);
		RaceSaveRequestDto request = builder.title("2026 부산 바다 런")
			.description("광안리 해변을 달리는 대회")
			.location("광안리 해수욕장")
			.region("부산광역시", "수영구")
			.raceDate(raceDate)
			.applicationCloseAt(applicationCloseAt)
			.refundDeadline(refundDeadline)
			.crewCount(5, 30)
			.course(course(), halfCourse())
			.build();

		RegisteredRace registered = register(request);

		assertThat(registered.courseIds()).hasSize(2);
		// 판매자·종목 목록은 지연 로딩이라 트랜잭션 안에서 읽는다
		transactionTemplate.executeWithoutResult(tx -> {
			Race savedRace = raceRepository.findById(registered.raceId()).orElseThrow();
			assertThat(savedRace.getSeller().getId()).isEqualTo(SELLER_ID);
			assertThat(savedRace.getTitle()).isEqualTo("2026 부산 바다 런");
			assertThat(savedRace.getDescription()).isEqualTo("광안리 해변을 달리는 대회");
			assertThat(savedRace.getLocation()).isEqualTo("광안리 해수욕장");
			assertThat(savedRace.getRegionSido()).isEqualTo("부산광역시");
			assertThat(savedRace.getRegionSigungu()).isEqualTo("수영구");
			assertThat(savedRace.getRaceDate()).isEqualTo(raceDate);
			assertThat(savedRace.getCrewSaleStartAt()).isNull();
			assertThat(savedRace.getCrewSaleEndAt()).isNull();
			assertThat(savedRace.getIndividualOpenAt()).isEqualTo(request.getIndividualOpenAt());
			assertThat(savedRace.getApplicationCloseAt()).isEqualTo(applicationCloseAt);
			assertThat(savedRace.getSaleOpenDate()).isEqualTo(request.getIndividualOpenAt().toLocalDate());
			assertThat(savedRace.getRefundDeadline()).isEqualTo(refundDeadline);
			assertThat(savedRace.getRefundPolicy()).isEqualTo(request.getRefundPolicy());
			assertThat(savedRace.getCrewMinCount()).isEqualTo(5);
			assertThat(savedRace.getCrewMaxCount()).isEqualTo(30);
			assertThat(savedRace.getSaleStatus()).isEqualTo(RaceSaleStatus.SCHEDULED);
			assertThat(savedRace.getScheduleChangedAt()).isNull();
			assertThat(savedRace.getCanceledAt()).isNull();
			assertThat(savedRace.getCreateDate()).isNotNull();
			assertThat(savedRace.getCourses()).extracting(RaceCourse::getId)
				.containsExactlyInAnyOrderElementsOf(registered.courseIds());

			RaceCourse tenK = raceCourseRepository.findById(registered.courseIds().get(0)).orElseThrow();
			assertThat(tenK.getRace().getId()).isEqualTo(registered.raceId());
			assertThat(tenK.getName()).isEqualTo("10K");
			assertThat(tenK.getCourseType()).isEqualTo(CourseType.KM_10);
			assertThat(tenK.getSurface()).isEqualTo(CourseSurface.ROAD);
			assertThat(tenK.getDistanceKm()).isEqualByComparingTo("10");
			assertThat(tenK.getTimeLimitMinutes()).isEqualTo(90);
			assertThat(tenK.getPrice()).isEqualTo(45_000);
			assertThat(tenK.isKitIncluded()).isFalse();
			assertThat(tenK.getKitPrice()).isZero();
			assertThat(tenK.getTshirtSizeOption()).isEmpty();
			assertThat(tenK.getMinAge()).isEqualTo(14);
			assertThat(tenK.getMaxAge()).isNull();
			assertThat(tenK.getCrewQuota()).isZero();
			assertThat(tenK.getIndividualQuota()).isEqualTo(100);
			assertThat(tenK.getStatus()).isEqualTo(CourseStatus.OPEN);
			// 확보·판매 수와 옮긴 크루 자리는 0에서 시작한다
			assertThat(tenK.getIndividualReserved()).isZero();
			assertThat(tenK.getIndividualSold()).isZero();
			assertThat(tenK.getCrewReserved()).isZero();
			assertThat(tenK.getCrewSold()).isZero();
			assertThat(tenK.getMovedQuota()).isZero();
			assertThat(tenK.getCrewClosedAt()).isNull();

			RaceCourse half = raceCourseRepository.findById(registered.courseIds().get(1)).orElseThrow();
			assertThat(half.getRace().getId()).isEqualTo(registered.raceId());
			assertThat(half.getName()).isEqualTo("하프");
			assertThat(half.getCourseType()).isEqualTo(CourseType.HALF);
			assertThat(half.getSurface()).isEqualTo(CourseSurface.TRAIL);
			assertThat(half.getDistanceKm()).isEqualByComparingTo("21.1");
			assertThat(half.getTimeLimitMinutes()).isEqualTo(180);
			assertThat(half.getPrice()).isEqualTo(60_000);
			assertThat(half.isKitIncluded()).isTrue();
			assertThat(half.getKitPrice()).isEqualTo(5_000);
			assertThat(half.getTshirtSizeOption()).containsExactly(TshirtSize.M);
			assertThat(half.getMinAge()).isEqualTo(20);
			assertThat(half.getMaxAge()).isEqualTo(60);
			assertThat(half.getCrewQuota()).isZero();
			assertThat(half.getIndividualQuota()).isEqualTo(300);
			assertThat(half.getStatus()).isEqualTo(CourseStatus.OPEN);
		});
	}

	@Test
	@DisplayName("티셔츠 사이즈를 XXL, S, XL, M 순서로 보내도 작은 사이즈부터 S, M, XL, XXL로 저장한다")
	void saveTshirtSizesFromSmallest() throws Exception {
		RegisteredRace registered = register(upcomingRace()
			.course(course().kit(5_000, TshirtSize.XXL, TshirtSize.S, TshirtSize.XL, TshirtSize.M))
			.build());

		RaceCourse savedCourse = raceCourseRepository.findById(registered.courseId()).orElseThrow();
		assertThat(savedCourse.getTshirtSizeOption())
			.containsExactly(TshirtSize.S, TshirtSize.M, TshirtSize.XL, TshirtSize.XXL);
	}

	@Test
	@DisplayName("등록 요청의 종목에 courseId(Integer.MAX_VALUE)를 넣어도 무시하고 새 종목으로 저장한다")
	void ignoreCourseIdOnRegister() throws Exception {
		RegisteredRace registered = register(upcomingRace().course(course().courseId(Integer.MAX_VALUE)).build());

		assertThat(registered.courseId()).isNotEqualTo(Integer.MAX_VALUE);
		assertThat(raceCourseRepository.findById(registered.courseId())).isPresent();
		assertThat(raceCourseRepository.findById(Integer.MAX_VALUE)).isEmpty();
	}

	@Test
	@DisplayName("등록하면 종목마다 판매 예정(SCHEDULED) 검색 문서를 만든다 (10K 제한 65분 → 페이스 6분, 5K 제한 40분 → 페이스 8분)")
	void indexSearchDocuments() throws Exception {
		RaceSaveRequestDto request = upcomingRace().title("2026 한강 나이트 런")
			.description("한강 야경을 보며 달리는 대회")
			.course(course().timeLimitMinutes(65).age(14, 70),
				course().name("5K").type(CourseType.KM_5).distanceKm("5").timeLimitMinutes(40))
			.build();

		RegisteredRace registered = register(request);

		LocalDateTime raceCreatedAt = raceRepository.findById(registered.raceId()).orElseThrow().getCreateDate();
		// 문서 ID는 종목 ID다 (ID로 읽으면 색인 직후에도 바로 보인다)
		RaceCourseDocument tenK = raceCourseDocumentRepository.findById(String.valueOf(registered.courseIds().get(0)))
			.orElseThrow();
		assertThat(tenK.getRaceId()).isEqualTo(String.valueOf(registered.raceId()));
		assertThat(tenK.getRaceTitle()).isEqualTo("2026 한강 나이트 런");
		assertThat(tenK.getCourseName()).isEqualTo("10K");
		assertThat(tenK.getCourseLabel()).isEqualTo("2026 한강 나이트 런 10K");
		assertThat(tenK.getDescription()).isEqualTo("한강 야경을 보며 달리는 대회");
		assertThat(tenK.getLocation()).isEqualTo("서울숲 일대");
		assertThat(tenK.getRegionSido()).isEqualTo("서울특별시");
		assertThat(tenK.getRegionSigungu()).isEqualTo("성동구");
		assertThat(tenK.getSaleStatus()).isEqualTo(RaceSaleStatus.SCHEDULED);
		assertThat(tenK.getCourseStatus()).isEqualTo(CourseStatus.OPEN);
		assertThat(tenK.getCourseType()).isEqualTo(CourseType.KM_10);
		assertThat(tenK.getSurface()).isEqualTo(CourseSurface.ROAD);
		assertThat(tenK.getRaceDate()).isEqualTo(request.getRaceDate());
		assertThat(tenK.getApplicationCloseAt()).isEqualTo(request.getApplicationCloseAt());
		assertThat(tenK.getSaleOpenDate()).isEqualTo(request.getIndividualOpenAt().toLocalDate());
		// 문서의 날짜는 밀리초까지만 저장되므로 대회 등록 일시와 1초 안으로 맞는지 본다
		assertThat(tenK.getRaceCreatedAt()).isCloseTo(raceCreatedAt, within(1, ChronoUnit.SECONDS));
		assertThat(tenK.getPrice()).isEqualTo(45_000);
		assertThat(tenK.isKitIncluded()).isFalse();
		assertThat(tenK.getDistanceKm()).isEqualTo(10.0);
		assertThat(tenK.getPaceMinutes()).isEqualTo(6);
		assertThat(tenK.getMinAge()).isEqualTo(14);
		assertThat(tenK.getMaxAge()).isEqualTo(70);

		RaceCourseDocument fiveK = raceCourseDocumentRepository.findById(String.valueOf(registered.courseIds().get(1)))
			.orElseThrow();
		assertThat(fiveK.getRaceId()).isEqualTo(String.valueOf(registered.raceId()));
		assertThat(fiveK.getCourseLabel()).isEqualTo("2026 한강 나이트 런 5K");
		assertThat(fiveK.getDescription()).isEqualTo("한강 야경을 보며 달리는 대회");
		assertThat(fiveK.getCourseType()).isEqualTo(CourseType.KM_5);
		assertThat(fiveK.getSaleStatus()).isEqualTo(RaceSaleStatus.SCHEDULED);
		assertThat(fiveK.getRaceCreatedAt()).isCloseTo(raceCreatedAt, within(1, ChronoUnit.SECONDS));
		assertThat(fiveK.getDistanceKm()).isEqualTo(5.0);
		assertThat(fiveK.getPaceMinutes()).isEqualTo(8);
		assertThat(fiveK.getMaxAge()).isNull();
	}

	@Test
	@DisplayName("등록이 커밋되면 race.filelink.requested로 종목별 이미지 연결을 1번 요청한다 (소유자 회원 9001, refType RaceCourse, key 대회 ID)")
	void publishFileLink() throws Exception {
		RaceImageRequestDto tenKThumbnail = thumbnail();
		RaceImageRequestDto tenKDetail = image(FileType.DETAIL, 0);
		RaceImageRequestDto tenKSecondDetail = image(FileType.DETAIL, 1);
		RaceImageRequestDto halfThumbnail = thumbnail();
		RaceImageRequestDto halfKit = image(FileType.KIT, 0);

		RegisteredRace registered = register(upcomingRace()
			.course(course().images(tenKThumbnail, tenKDetail, tenKSecondDetail),
				halfCourse().images(halfThumbnail, halfKit))
			.build());

		List<String> payloads = outboxPayloads(KafkaTopics.RACE_FILE_LINK_REQUESTED);
		assertThat(payloads).hasSize(1);
		String payload = payloads.getFirst();
		int ownerId = JsonPath.read(payload, "$.ownerId");
		List<String> refTypes = JsonPath.read(payload, "$.refs[*].refType");
		List<Integer> refIds = JsonPath.read(payload, "$.refs[*].refId");
		List<Integer> tenKFileIds = JsonPath.read(payload, "$.refs[0].files[*].fileId");
		List<String> tenKFileTypes = JsonPath.read(payload, "$.refs[0].files[*].fileType");
		List<Integer> tenKSortNos = JsonPath.read(payload, "$.refs[0].files[*].sortNo");
		List<Integer> halfFileIds = JsonPath.read(payload, "$.refs[1].files[*].fileId");
		List<String> halfFileTypes = JsonPath.read(payload, "$.refs[1].files[*].fileType");

		// 파일 소유자는 판매자 ID(7001)가 아니라 등록한 회원 ID다 (파일을 올린 회원과 같아야 연결된다)
		assertThat(ownerId).isEqualTo(SELLER_MEMBER_ID);
		assertThat(refTypes).containsExactly("RaceCourse", "RaceCourse");
		assertThat(refIds).containsExactlyElementsOf(registered.courseIds());
		assertThat(tenKFileIds)
			.containsExactly(tenKThumbnail.getFileId(), tenKDetail.getFileId(), tenKSecondDetail.getFileId());
		assertThat(tenKFileTypes).containsExactly("THUMBNAIL", "DETAIL", "DETAIL");
		assertThat(tenKSortNos).containsExactly(0, 0, 1);
		assertThat(halfFileIds).containsExactly(halfThumbnail.getFileId(), halfKit.getFileId());
		assertThat(halfFileTypes).containsExactly("THUMBNAIL", "KIT");
		// 같은 대회의 메시지가 순서대로 처리되도록 대회 ID를 key로 보낸다
		assertThat(outboxKeys(KafkaTopics.RACE_FILE_LINK_REQUESTED))
			.containsExactly(String.valueOf(registered.raceId()));
	}

	// ---------- 인증·권한 ----------

	@Test
	@DisplayName("X-Member-Id 헤더 없이 등록하면 401이고 아무것도 저장하지 않는다")
	void rejectWithoutMemberHeader() throws Exception {
		expectError(postRace(null, upcomingRace().build()), HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		assertNothingSaved();
	}

	@Test
	@DisplayName("X-Member-Id 헤더가 숫자가 아니면(abc) 400이다")
	void rejectNonNumericMemberHeader() throws Exception {
		ResultActions result = mockMvc.perform(post(RACES_PATH)
			.header("X-Member-Id", "abc")
			.header("Idempotency-Key", newIdempotencyKey())
			.contentType(MediaType.APPLICATION_JSON)
			.content(jsonConverter.toJson(upcomingRace().build())));

		expectError(result, HttpStatus.BAD_REQUEST, "요청 값의 형식이 올바르지 않습니다.");
		assertNothingSaved();
	}

	@Test
	@DisplayName("판매자가 아닌 회원 9999가 등록하면 403이고 아무것도 저장하지 않는다")
	void rejectNotSeller() throws Exception {
		expectError(postRace(NOT_SELLER_MEMBER_ID, upcomingRace().build()), HttpStatus.FORBIDDEN,
			"승인된 판매자만 대회를 등록·수정할 수 있습니다.");
		assertNothingSaved();
	}

	@Test
	@DisplayName("판매자 신청이 승인 대기(PENDING)인 회원 9003이 등록하면 403이다")
	void rejectPendingSeller() throws Exception {
		LocalDateTime now = LocalDateTime.now();
		ProductMember member = productMemberRepository.save(
			new ProductMember(PENDING_SELLER_MEMBER_ID, now, now, "러너" + PENDING_SELLER_MEMBER_ID));
		productSellerRepository.save(new ProductSeller(PENDING_SELLER_ID, member, SellerType.INDIVIDUAL, null, null,
			"박러너", SellerStatus.PENDING, now, null));

		expectError(postRace(PENDING_SELLER_MEMBER_ID, upcomingRace().build()), HttpStatus.FORBIDDEN,
			"승인된 판매자만 대회를 등록·수정할 수 있습니다.");
		assertNothingSaved();
	}

	// ---------- 요청 DTO 검증 애노테이션 ----------

	@ParameterizedTest(name = "{0} → 400 \"{2}\"")
	@MethodSource("invalidInputs")
	@DisplayName("요청 값 하나가 DTO 검증 애노테이션에 걸리면 400이고 그 값의 메시지를 돌려준다")
	void rejectInvalidInput(String situation, RaceSaveRequestDto request, String message) throws Exception {
		assertRejected(request, message);
	}

	// 한 요청에 위반은 하나만 넣는다 (위반이 여럿이면 어느 메시지가 먼저 나올지 정해져 있지 않다)
	static Stream<Arguments> invalidInputs() {
		return Stream.of(
			Arguments.of("대회 이름이 공백", upcomingRace().title("  ").build(), "대회 이름을 작성해주세요."),
			Arguments.of("대회 이름이 201자", upcomingRace().title("가".repeat(201)).build(), "대회 이름은 200자 이하로 작성해주세요."),
			// 빌더는 환불 기준일이 없으면 대회 날짜로 채우므로, 대회 일시를 비울 때는 환불 기준일을 직접 넣는다
			Arguments.of("대회 일시 없음", upcomingRace().raceDate(null).refundDeadline(LocalDate.now().plusDays(15)).build(),
				"대회 일시를 작성해주세요."),
			Arguments.of("크루 최소 인원 4명", upcomingRace().crewCount(4, null).build(), "크루 최소 인원은 5명 이상이어야 합니다."),
			Arguments.of("종목 0개", withCourses(upcomingRace().build(), List.of()), "종목을 1개 이상 작성해주세요."),
			Arguments.of("참가비 0원", upcomingRace().course(course().price(0)).build(), "참가비는 0원보다 커야 합니다."),
			Arguments.of("거리 10.555km", upcomingRace().course(course().distanceKm("10.555")).build(),
				"거리는 999.99km 이하, 소수 둘째 자리까지 작성해주세요."),
			Arguments.of("참가 최소 나이 13세", upcomingRace().course(course().age(13, null)).build(),
				"참가 최소 나이는 만 14세 이상이어야 합니다."),
			Arguments.of("개인 정원 -1명", upcomingRace().course(course().quota(0, -1)).build(), "개인 정원은 0 이상이어야 합니다."),
			Arguments.of("이미지 파일 ID 0",
				upcomingRace().course(course().images(new RaceImageRequestDto(0, FileType.THUMBNAIL, 0))).build(),
				"파일 ID는 0보다 커야 합니다.")
		);
	}

	// ---------- RaceRequestValidator: 인원·일정 ----------

	@Test
	@DisplayName("크루 최대 인원 5명이 최소 인원 10명보다 적으면 400")
	void rejectCrewMaxBelowMin() throws Exception {
		assertRejected(upcomingRace().crewCount(10, 5).build(), "크루 최대 인원은 최소 인원 이상이어야 합니다.");
	}

	@Test
	@DisplayName("크루 예매 시작 일시만 보내거나 마감 일시만 보내면 400")
	void rejectCrewSaleWithoutPair() throws Exception {
		RaceBuilder startOnly = upcomingRace();
		assertRejected(startOnly.crewSale(startOnly.now().plusDays(1), null).build(),
			"크루 예매 시작·마감 일시는 함께 작성하거나 함께 비워주세요.");

		RaceBuilder endOnly = upcomingRace();
		assertRejected(endOnly.crewSale(null, endOnly.now().plusDays(2)).build(),
			"크루 예매 시작·마감 일시는 함께 작성하거나 함께 비워주세요.");
	}

	@Test
	@DisplayName("크루 예매 마감 일시가 시작 일시(내일)와 같은 시각이면 400")
	void rejectCrewSaleEndNotAfterStart() throws Exception {
		RaceBuilder builder = upcomingRace();
		LocalDateTime crewSaleAt = builder.now().plusDays(1);

		assertRejected(builder.crewSale(crewSaleAt, crewSaleAt).build(), "크루 예매 마감 일시는 시작 일시보다 늦어야 합니다.");
	}

	@Test
	@DisplayName("개인 예매 오픈이 크루 예매 마감일 다음 날 05:59면 400이고, 06:00 정각이면 등록된다")
	void rejectIndividualOpenBeforeCrewNextDay() throws Exception {
		RaceBuilder builder = upcomingRace();
		LocalDateTime crewSaleEndAt = builder.now().plusDays(3);
		LocalDateTime earliestOpenAt = crewSaleEndAt.toLocalDate().plusDays(1).atTime(6, 0);
		builder.crewSale(builder.now().plusDays(2), crewSaleEndAt);

		assertRejected(builder.individualOpenAt(earliestOpenAt.minusMinutes(1)).build(),
			"개인 예매 오픈 일시는 크루 예매 마감일 다음 날 06:00 이후여야 합니다.");

		register(builder.individualOpenAt(earliestOpenAt).build());
	}

	@Test
	@DisplayName("접수 마감 일시가 개인 예매 오픈 일시(이틀 뒤)와 같은 시각이면 400")
	void rejectApplicationCloseNotAfterIndividualOpen() throws Exception {
		RaceBuilder builder = upcomingRace();

		assertRejected(builder.applicationCloseAt(builder.now().plusDays(2)).build(),
			"접수 마감 일시는 개인 예매 오픈 일시보다 늦어야 합니다.");
	}

	@Test
	@DisplayName("크루 예매 없이 개인 예매 오픈이 내일 23:59면 판매 시작일이 이틀 뒤보다 빨라 400이고, 이틀 뒤 00:00이면 등록된다")
	void rejectSaleOpenBeforeTwoDays() throws Exception {
		RaceBuilder builder = race();
		LocalDate twoDaysLater = builder.now().toLocalDate().plusDays(2);

		assertRejected(builder.individualOpenAt(twoDaysLater.atStartOfDay().minusMinutes(1)).build(),
			SALE_OPEN_TOO_SOON_MESSAGE.formatted(twoDaysLater.format(MESSAGE_DATE)));

		register(builder.individualOpenAt(twoDaysLater.atStartOfDay()).build());
	}

	@Test
	@DisplayName("크루 예매 없이 개인 예매 오픈이 1분 전이면 판매 시작일이 지나 400이고 아무것도 저장하지 않는다")
	void rejectPassedIndividualOpen() throws Exception {
		RaceBuilder builder = race();

		assertRejected(builder.individualOpenAt(builder.now().minusMinutes(1)).build(),
			SALE_OPEN_TOO_SOON_MESSAGE.formatted(builder.now().toLocalDate().plusDays(2).format(MESSAGE_DATE)));
	}

	@Test
	@DisplayName("개인 예매 오픈(사흘 뒤)은 이틀 뒤 이후여도 더 이른 크루 예매 시작이 내일이면 판매 시작일이 이틀 뒤보다 빨라 400")
	void rejectCrewSaleStartBeforeTwoDays() throws Exception {
		RaceBuilder builder = race();
		LocalDate today = builder.now().toLocalDate();

		assertRejected(builder.crewSale(today.plusDays(1).atTime(10, 0), today.plusDays(1).atTime(20, 0))
			.individualOpenAt(today.plusDays(3).atTime(10, 0))
			.build(), SALE_OPEN_TOO_SOON_MESSAGE.formatted(today.plusDays(2).format(MESSAGE_DATE)));
	}

	@Test
	@DisplayName("접수 마감 일시가 대회 일시보다 1분 늦으면 400이고, 대회 일시와 같으면 등록된다")
	void rejectApplicationCloseAfterRaceDate() throws Exception {
		RaceBuilder builder = upcomingRace();
		LocalDateTime raceDate = builder.now().plusDays(15);
		builder.raceDate(raceDate);

		assertRejected(builder.applicationCloseAt(raceDate.plusMinutes(1)).build(),
			"접수 마감 일시는 대회 일시보다 늦을 수 없습니다.");

		register(builder.applicationCloseAt(raceDate).build());
	}

	@Test
	@DisplayName("환불 기준일이 대회 날짜 다음 날이면 400이고, 대회 날짜와 같으면 등록된다")
	void rejectRefundDeadlineAfterRaceDate() throws Exception {
		RaceBuilder builder = upcomingRace();
		LocalDateTime raceDate = builder.now().plusDays(15);
		builder.raceDate(raceDate);

		assertRejected(builder.refundDeadline(raceDate.toLocalDate().plusDays(1)).build(),
			"환불 기준일은 대회 날짜보다 늦을 수 없습니다.");

		register(builder.refundDeadline(raceDate.toLocalDate()).build());
	}

	// ---------- RaceRequestValidator: 종목 ----------

	@Test
	@DisplayName("두 번째 종목(하프)의 최대 나이 20세가 최소 나이 30세보다 적으면 400이고, 메시지에 종목명이 붙는다")
	void rejectMaxAgeBelowMinAge() throws Exception {
		assertRejected(upcomingRace().course(course(), halfCourse().age(30, 20)).build(),
			"참가 최대 나이는 최소 나이 이상이어야 합니다. (종목: 하프)");
	}

	@Test
	@DisplayName("키트 금액 45,001원이 참가비 45,000원보다 크면 400이고, 참가비와 같으면 등록된다")
	void rejectKitPriceOverPrice() throws Exception {
		assertRejected(upcomingRace().course(course().price(45_000).kit(45_001)).build(),
			"키트 금액은 참가비보다 클 수 없습니다. (종목: 10K)");

		register(upcomingRace().course(course().price(45_000).kit(45_000)).build());
	}

	@Test
	@DisplayName("크루 예매가 없는 대회의 종목에 크루 정원 10명을 넣으면 400")
	void rejectCrewQuotaWithoutCrewSale() throws Exception {
		assertRejected(upcomingRace().course(course().quota(10, 90)).build(),
			"크루 예매가 없는 대회는 크루 정원을 0으로 작성해주세요. (종목: 10K)");
	}

	@Test
	@DisplayName("크루 정원과 개인 정원이 모두 0이면 400")
	void rejectZeroQuota() throws Exception {
		assertRejected(upcomingRace().course(course().quota(0, 0)).build(), "정원은 1명 이상이어야 합니다. (종목: 10K)");
	}

	@Test
	@DisplayName("키트가 없는 종목에 티셔츠 사이즈 M을 넣으면 400")
	void rejectTshirtSizeWithoutKit() throws Exception {
		assertRejected(upcomingRace().course(course().tshirtSizes(List.of(TshirtSize.M))).build(),
			"키트가 없는 종목은 티셔츠 사이즈를 작성할 수 없습니다. (종목: 10K)");
	}

	@Test
	@DisplayName("티셔츠 사이즈를 M, L, M으로 M을 두 번 넣으면 400")
	void rejectDuplicateTshirtSize() throws Exception {
		assertRejected(upcomingRace().course(course().kit(0, TshirtSize.M, TshirtSize.L, TshirtSize.M)).build(),
			"같은 티셔츠 사이즈(M)를 두 번 작성할 수 없습니다. (종목: 10K)");
	}

	// ---------- RaceRequestValidator: 이미지 ----------

	@Test
	@DisplayName("10K 종목의 대표 이미지가 0장(이미지 없음, 소개 이미지만 1장)이거나 2장이면 400")
	void rejectWithoutSingleThumbnail() throws Exception {
		String message = "10K 종목의 대표 이미지를 1장 연결해주세요.";

		assertRejected(upcomingRace().course(course().images()).build(), message);
		assertRejected(upcomingRace().course(course().images(image(FileType.DETAIL, 0))).build(), message);
		assertRejected(upcomingRace().course(course().images(thumbnail(), thumbnail())).build(), message);
	}

	@Test
	@DisplayName("10K 종목에 키트 이미지를 2장 넣으면(최대 1장) 400")
	void rejectTooManyKitImages() throws Exception {
		assertRejected(upcomingRace()
			.course(course().images(thumbnail(), image(FileType.KIT, 0), image(FileType.KIT, 1)))
			.build(), "10K 종목의 키트 이미지 최대 개수(1개)를 넘었습니다.");
	}

	@Test
	@DisplayName("종목에 크루원 명단(ROSTER) 파일을 넣으면 400")
	void rejectRosterOnCourse() throws Exception {
		RaceImageRequestDto roster = image(FileType.ROSTER, 0);

		assertRejected(upcomingRace().course(course().images(thumbnail(), roster)).build(),
			"10K 종목에는 크루원 명단을 연결할 수 없습니다. (fileId: %d, fileType: ROSTER)".formatted(roster.getFileId()));
	}

	@Test
	@DisplayName("10K와 하프 두 종목에 같은 대표 이미지 파일을 넣으면 400")
	void rejectSameFileInTwoCourses() throws Exception {
		RaceImageRequestDto shared = thumbnail();

		assertRejected(upcomingRace().course(course().images(shared), halfCourse().images(shared)).build(),
			"같은 파일을 두 번 연결할 수 없습니다. (fileId: %d, fileType: THUMBNAIL)".formatted(shared.getFileId()));
	}

	@Test
	@DisplayName("한 종목에 같은 파일을 대표 이미지와 소개 이미지로 두 번 넣으면 400")
	void rejectSameFileTwiceInOneCourse() throws Exception {
		RaceImageRequestDto thumbnailImage = thumbnail();
		RaceImageRequestDto sameFileAsDetail = new RaceImageRequestDto(thumbnailImage.getFileId(), FileType.DETAIL, 0);

		// 두 번째로 나온 항목(소개 이미지)을 메시지에 넣는다
		assertRejected(upcomingRace().course(course().images(thumbnailImage, sameFileAsDetail)).build(),
			"같은 파일을 두 번 연결할 수 없습니다. (fileId: %d, fileType: DETAIL)".formatted(thumbnailImage.getFileId()));
	}

	// ---------- 요청 본문 형식 ----------

	@Test
	@DisplayName("요청 본문이 끊긴 JSON이거나, 비어 있거나, 없는 종목 구분(10K)을 보내면 400")
	void rejectMalformedBody() throws Exception {
		String message = "요청 본문 형식이 올바르지 않습니다.";
		String validBody = jsonConverter.toJson(upcomingRace().build());

		expectError(postRaw("{\"title\": \"서울 런\","), HttpStatus.BAD_REQUEST, message);
		expectError(postRaw(""), HttpStatus.BAD_REQUEST, message);
		expectError(postRaw(validBody.replace("\"KM_10\"", "\"10K\"")), HttpStatus.BAD_REQUEST, message);
		assertNothingSaved();
	}

	// ---------- 도우미 ----------

	// 등록할 수 있는 대회 (개인 예매 이틀 뒤 오픈). 판매 시작일은 오늘로부터 이틀 뒤부터 정할 수 있다
	private static RaceBuilder upcomingRace() {
		RaceBuilder builder = race();
		return builder.individualOpenAt(builder.now().plusDays(2));
	}

	// 하프 종목 (트레일 21.1km, 제한 180분, 참가비 60,000원, 키트 5,000원·티셔츠 M, 20~60세, 개인 정원 300)
	private static CourseBuilder halfCourse() {
		return course().name("하프")
			.type(CourseType.HALF)
			.surface(CourseSurface.TRAIL)
			.distanceKm("21.1")
			.timeLimitMinutes(180)
			.price(60_000)
			.kit(5_000, TshirtSize.M)
			.age(20, 60)
			.quota(0, 300);
	}

	// 요청 빌더는 종목이 없으면 기본 종목을 넣으므로, 종목 목록만 바꾼 요청을 직접 만든다
	private static RaceSaveRequestDto withCourses(RaceSaveRequestDto request, List<RaceCourseSaveRequestDto> courses) {
		return new RaceSaveRequestDto(request.getTitle(), request.getDescription(), request.getLocation(),
			request.getRegionSido(), request.getRegionSigungu(), request.getRaceDate(), request.getCrewSaleStartAt(),
			request.getCrewSaleEndAt(), request.getIndividualOpenAt(), request.getApplicationCloseAt(),
			request.getRefundDeadline(), request.getRefundPolicy(), request.getCrewMinCount(),
			request.getCrewMaxCount(), courses);
	}

	private ResultActions postRaw(String content) throws Exception {
		return mockMvc.perform(post(RACES_PATH)
			.header("X-Member-Id", SELLER_MEMBER_ID)
			.header("Idempotency-Key", newIdempotencyKey())
			.contentType(MediaType.APPLICATION_JSON)
			.content(content));
	}

	// 400과 메시지를 확인하고, 거절된 요청이 아무것도 남기지 않았는지 본다
	private void assertRejected(RaceSaveRequestDto request, String message) throws Exception {
		expectError(postRace(SELLER_MEMBER_ID, request), HttpStatus.BAD_REQUEST, message);
		assertNothingSaved();
	}

	// 오류 응답은 RsData(data: null)다. doesNotExist()는 null 값과 빠진 필드를 모두 통과시킨다
	private void expectError(ResultActions result, HttpStatus httpStatus, String message) throws Exception {
		result.andExpect(status().is(httpStatus.value()))
			.andExpect(jsonPath("$.status").value(httpStatus.value()))
			.andExpect(jsonPath("$.message").value(message))
			.andExpect(jsonPath("$.data").doesNotExist());
	}

	// 대회·종목 행이 없고 파일 연결 메시지도 보내지 않았다
	private void assertNothingSaved() {
		assertThat(raceRepository.count()).isZero();
		assertThat(raceCourseRepository.count()).isZero();
		assertThat(outboxPayloads(KafkaTopics.RACE_FILE_LINK_REQUESTED)).isEmpty();
	}
}
