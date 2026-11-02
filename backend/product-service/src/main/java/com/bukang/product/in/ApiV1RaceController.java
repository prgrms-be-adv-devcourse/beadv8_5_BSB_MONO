package com.bukang.product.in;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.global.web.IdempotencyKeyHeader;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.common.shared.member.web.MemberIdHeader;
import com.bukang.product.app.ProductFacade;
import com.bukang.product.app.RaceRegisterResult;
import com.bukang.product.domain.CourseSurface;
import com.bukang.product.domain.CourseType;
import com.bukang.product.dto.RaceDetailDto;
import com.bukang.product.dto.RacePageDto;
import com.bukang.product.dto.RaceSaveDto;
import com.bukang.product.dto.RaceSaveRequestDto;
import com.bukang.product.dto.RaceSearchCondition;
import com.bukang.product.dto.RaceSort;
import com.bukang.product.dto.RaceSuggestionDto;
import com.bukang.product.dto.SellerRaceDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

// 게이트웨이가 경로 앞부분을 자르지 않으므로(StripPrefix 금지, PRO-17) 서비스 이름(/product)부터 쓴다
@Tag(name = "Race", description = "대회 등록, 수정, 취소, 목록·상세·내 대회 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/product/races")
public class ApiV1RaceController {
	// 응답 메시지에 보여 주는 날짜·일시 형식 (예: 10월 20일 / 10월 20일 09:00)
	private static final DateTimeFormatter MESSAGE_DATE = DateTimeFormatter.ofPattern("M월 d일");
	private static final DateTimeFormatter MESSAGE_DATE_TIME = DateTimeFormatter.ofPattern("M월 d일 HH:mm");
	private static final int MAX_PAGE_SIZE = 100;
	// 같은 Idempotency-Key로 다시 온 등록 요청 (처음 등록한 대회를 돌려준다)
	private static final String REGISTER_EXISTING_MESSAGE = "이미 처리한 대회 등록 요청입니다.";

	private final ProductFacade productFacade;

	// ResponseEntity.created()의 201은 springdoc이 추론하지 못하므로 응답 코드를 직접 명시한다
	// 에러 응답은 GlobalExceptionHandler, ProductExceptionHandler가 RsData(data: null) 형식으로 반환한다
	@PostMapping
	@Operation(summary = "대회 등록",
		description = "승인된 판매자가 대회와 종목을 판매 예정(SCHEDULED)으로 등록하고, 등록하면 바로 공개된다. "
			+ "판매 시작일(saleOpenDate)은 크루 예매 시작·개인 예매 오픈 중 이른 날짜로 채우고, 오늘로부터 이틀 뒤부터 정할 수 있다. "
			+ "판매 시작일 0시에 상태 배치(5분마다)가 판매 중으로 바꾸고, 이때부터 판매 시작 뒤 수정 규칙이 걸린다. "
			+ "판매 상태는 판매자 화면용이고, 구매자는 크루 예매 시작·개인 예매 오픈 시각부터 예매할 수 있다. "
			+ "이미지는 종목마다 받고(대표 이미지 1장 필수), 등록이 커밋된 뒤 file-service에 연결을 요청한다. "
			+ "파일이 없거나 본인이 올린 파일이 아니어서 연결에 실패하면 응답에는 나오지 않고 file-service 로그에 남는다. "
			+ "같은 판매자가 같은 Idempotency-Key로 다시 보내면 새로 등록하지 않고 처음 대회를 200으로 돌려준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "등록 성공",
			content = @Content(examples = @ExampleObject(value = RaceApiExamples.REGISTER_SUCCESS))),
		@ApiResponse(responseCode = "200", description = "같은 Idempotency-Key로 이미 등록한 대회 (새로 등록하지 않음)",
			content = @Content(examples = @ExampleObject(value = RaceApiExamples.REGISTER_EXISTING))),
		@ApiResponse(responseCode = "400",
			description = "입력값 검증 실패, 요청 본문 형식 오류, 날짜 순서·인원·나이·정원·키트 금액·티셔츠 사이즈 오류, "
				+ "판매 시작일이 이틀 뒤보다 빠름, 이미지 용도·개수 오류, 같은 파일 중복, 회원 ID 형식 오류·0 이하 "
				+ "또는 Idempotency-Key 헤더 없음·길이 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = RaceApiExamples.REGISTER_INVALID_INPUT),
				@ExampleObject(name = "요청 본문 형식 오류", value = RaceApiExamples.INVALID_BODY),
				@ExampleObject(name = "날짜 순서 오류", value = RaceApiExamples.INVALID_SCHEDULE),
				@ExampleObject(name = "판매 시작일이 이틀 뒤보다 빠름", value = RaceApiExamples.SALE_OPEN_TOO_SOON),
				@ExampleObject(name = "키트 금액이 참가비보다 큼", value = RaceApiExamples.KIT_PRICE_OVER_PRICE),
				@ExampleObject(name = "키트 없는 종목의 티셔츠 사이즈", value = RaceApiExamples.TSHIRT_SIZE_WITHOUT_KIT),
				@ExampleObject(name = "대표 이미지 없음", value = RaceApiExamples.THUMBNAIL_REQUIRED),
				@ExampleObject(name = "같은 파일 중복", value = RaceApiExamples.DUPLICATE_FILE),
				@ExampleObject(name = "회원 ID 형식 오류", value = RaceApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = RaceApiExamples.INVALID_MEMBER_ID),
				@ExampleObject(name = "Idempotency-Key 헤더 없음", value = RaceApiExamples.MISSING_IDEMPOTENCY_KEY),
				@ExampleObject(name = "Idempotency-Key 길이 오류", value = RaceApiExamples.INVALID_IDEMPOTENCY_KEY)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "승인된 판매자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.SELLER_NOT_APPROVED)))
	})
	public ResponseEntity<RsData<RaceSaveDto>> register(
		@LoginMemberId long memberId,
		@Parameter(description = "중복 등록 방지 키. 같은 [등록하기] 재시도에는 같은 값을 보낸다 (예: UUID)",
			example = "6f1c2a7e-3b9d-4f0a-9c55-0d2e8b7a1f43")
		@RequestHeader(IdempotencyKeyHeader.NAME)
		@Size(min = 1, max = IdempotencyKeyHeader.MAX_LENGTH, message = IdempotencyKeyHeader.INVALID_MESSAGE)
		String idempotencyKey,
		@Valid @RequestBody RaceSaveRequestDto request
	) {
		RaceRegisterResult result = productFacade.registerRace(memberId, idempotencyKey, request);
		RaceSaveDto race = result.race();
		return switch (result.outcome()) {
			case CREATED -> ResponseEntity.created(URI.create("/api/v1/product/races/" + race.getRaceId()))
				.body(RsData.of(HttpStatus.CREATED, registerMessage(race, request), race));
			case EXISTING -> ResponseEntity.ok()
				.body(RsData.of(HttpStatus.OK, REGISTER_EXISTING_MESSAGE, race));
		};
	}

	// 판매 중 표시는 판매 시작일 0시에, 예매는 크루 예매 시작·개인 예매 오픈 시각에 열린다는 것을 함께 알린다
	//   예: "대회를 등록했습니다. 10월 20일 0시부터 판매 중으로 표시되고, 개인 예매는 10월 20일 09:00에 열립니다."
	private static String registerMessage(RaceSaveDto race, RaceSaveRequestDto request) {
		String openTimes = "개인 예매는 %s".formatted(request.getIndividualOpenAt().format(MESSAGE_DATE_TIME));
		if (request.getCrewSaleStartAt() != null) {
			openTimes = "크루 예매는 %s, ".formatted(request.getCrewSaleStartAt().format(MESSAGE_DATE_TIME)) + openTimes;
		}
		return "대회를 등록했습니다. %s 0시부터 판매 중으로 표시되고, %s에 열립니다."
			.formatted(race.getSaleOpenDate().format(MESSAGE_DATE), openTimes);
	}

	@PutMapping("/{raceId}")
	@Operation(summary = "대회 수정",
		description = "본인이 등록한 대회를 등록과 같은 모양의 요청으로 통째로 바꾼다. 종목은 courseId가 있으면 수정하고 없으면 추가하며, "
			+ "요청에 없는 기존 종목은 뺀다. 이미지 목록도 통째로 바뀌어, 수정이 커밋된 뒤 file-service에 다시 연결을 요청한다. "
			+ "취소된 대회와 판매가 끝난 대회(대회일 다음 날 0시부터)는 수정할 수 없다. "
			+ "판매 시작 전에 판매 시작일이 바뀌면 등록과 같이 오늘로부터 이틀 뒤부터 정할 수 있다. "
			+ "판매가 시작된 뒤(판매 시작일 0시부터)에는 판매 시작일을 정하는 일정(크루 예매가 있으면 크루 예매 시작, "
			+ "없으면 개인 예매 오픈)을 바꿀 수 없다. "
			+ "판매가 시작된 뒤에는 참가비·키트 금액을 바꾸거나 환불 기준일을 앞당길 수 없고, 정원을 줄이거나 티셔츠 사이즈를 바꾸거나 "
			+ "종목을 뺄 수 없다(정원은 늘리기만). 종목의 참가 조건(거리·종목 구분·노면·제한 시간·나이 제한·키트 포함 여부)도 바꿀 수 없다. "
			+ "이미 지난 판매 일정(크루 예매 시작·마감, 개인 예매 오픈, 접수 마감)을 바꾸거나 "
			+ "일정을 지난 시각으로 옮길 수 없다(아직 오지 않은 일정은 앞당기거나 늦출 수 있다). "
			+ "판매가 시작된 뒤 대회 일시나 장소를 바꾸면 일정·장소 변경 안내 일시를 기록한다(이날부터 7일 동안 전액 환불).")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "수정 성공",
			content = @Content(examples = @ExampleObject(value = RaceApiExamples.UPDATE_SUCCESS))),
		@ApiResponse(responseCode = "400",
			description = "입력값 검증 실패, 요청 본문 형식 오류, 날짜 순서·인원·나이·정원·키트 금액·티셔츠 사이즈 오류, "
				+ "판매 시작 전 바뀐 판매 시작일이 이틀 뒤보다 빠름, 이미지 용도·개수 오류, 같은 파일 중복, "
				+ "이 대회의 종목이 아닌 종목 ID, 대회 ID·회원 ID 형식 오류 또는 회원 ID가 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = RaceApiExamples.REGISTER_INVALID_INPUT),
				@ExampleObject(name = "요청 본문 형식 오류", value = RaceApiExamples.INVALID_BODY),
				@ExampleObject(name = "날짜 순서 오류", value = RaceApiExamples.INVALID_SCHEDULE),
				@ExampleObject(name = "판매 시작일이 이틀 뒤보다 빠름", value = RaceApiExamples.SALE_OPEN_TOO_SOON),
				@ExampleObject(name = "키트 금액이 참가비보다 큼", value = RaceApiExamples.KIT_PRICE_OVER_PRICE),
				@ExampleObject(name = "키트 없는 종목의 티셔츠 사이즈", value = RaceApiExamples.TSHIRT_SIZE_WITHOUT_KIT),
				@ExampleObject(name = "대표 이미지 없음", value = RaceApiExamples.THUMBNAIL_REQUIRED),
				@ExampleObject(name = "같은 파일 중복", value = RaceApiExamples.DUPLICATE_FILE),
				@ExampleObject(name = "이 대회의 종목이 아님", value = RaceApiExamples.COURSE_NOT_IN_RACE),
				@ExampleObject(name = "대회 ID·회원 ID 형식 오류", value = RaceApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = RaceApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "승인된 판매자가 아님 또는 다른 판매자의 대회",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "승인된 판매자가 아님", value = RaceApiExamples.SELLER_NOT_APPROVED),
				@ExampleObject(name = "다른 판매자의 대회", value = RaceApiExamples.NOT_OWNER)
			})),
		@ApiResponse(responseCode = "404", description = "없는 대회",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.RACE_NOT_FOUND))),
		@ApiResponse(responseCode = "409",
			description = "판매 시작 뒤 판매 시작일을 정하는 일정 변경·참가비·키트 금액 변경·환불 기준일 앞당김, "
				+ "지난 판매 일정 변경·지난 시각으로 이동, 판매 시작 뒤 정원 줄이기·참가 조건 변경·티셔츠 사이즈 변경, "
				+ "종목 빼기, 취소된 대회 또는 판매가 끝난 대회",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "취소된 대회", value = RaceApiExamples.UPDATE_CANCELED),
				@ExampleObject(name = "판매가 끝난 대회", value = RaceApiExamples.UPDATE_CLOSED),
				@ExampleObject(name = "판매 시작일을 정하는 일정 변경", value = RaceApiExamples.SALE_OPEN_SCHEDULE_CHANGED),
				@ExampleObject(name = "참가비 변경", value = RaceApiExamples.PRICE_CHANGED),
				@ExampleObject(name = "키트 금액 변경", value = RaceApiExamples.KIT_PRICE_CHANGED),
				@ExampleObject(name = "환불 기준일 앞당김", value = RaceApiExamples.REFUND_DEADLINE_ADVANCED),
				@ExampleObject(name = "지난 판매 일정 변경", value = RaceApiExamples.SCHEDULE_PASSED),
				@ExampleObject(name = "판매 일정을 지난 시각으로", value = RaceApiExamples.SCHEDULE_TO_PAST),
				@ExampleObject(name = "정원 줄이기", value = RaceApiExamples.QUOTA_DECREASED),
				@ExampleObject(name = "참가 조건 변경", value = RaceApiExamples.COURSE_CONDITION_CHANGED),
				@ExampleObject(name = "티셔츠 사이즈 변경", value = RaceApiExamples.TSHIRT_SIZE_CHANGED),
				@ExampleObject(name = "종목 빼기", value = RaceApiExamples.COURSE_REMOVED)
			}))
	})
	public ResponseEntity<RsData<RaceSaveDto>> update(
		@LoginMemberId long memberId,
		@Parameter(description = "대회 ID", example = "1")
		@PathVariable long raceId,
		@Valid @RequestBody RaceSaveRequestDto request
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "대회를 수정했습니다.", productFacade.updateRace(memberId, raceId, request)));
	}

	@PostMapping("/{raceId}/cancel")
	@Operation(summary = "대회 취소",
		description = "본인이 등록한 대회를 취소한다. 판매가 끝나기 전(대회일 다음 날 0시 전)이면 판매 예정·판매 중·판매 중지 "
			+ "어느 상태든 취소할 수 있고, 되돌릴 수 없다. 결제 대기 자리는 모두 해제되고 공개 목록에서 빠진다. "
			+ "주문 서비스에 취소 메시지(Kafka order.race.cancel)를 보내 참가권을 일괄 환불하게 한다. "
			+ "취소된 대회는 수정·자리 확보를 할 수 없고, 상세는 등록한 판매자만 볼 수 있다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "취소 성공",
			content = @Content(examples = @ExampleObject(value = RaceApiExamples.CANCEL_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "대회 ID·회원 ID 형식 오류 또는 회원 ID가 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "대회 ID·회원 ID 형식 오류", value = RaceApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = RaceApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "승인된 판매자가 아님 또는 다른 판매자의 대회",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "승인된 판매자가 아님", value = RaceApiExamples.SELLER_NOT_APPROVED),
				@ExampleObject(name = "다른 판매자의 대회", value = RaceApiExamples.NOT_OWNER)
			})),
		@ApiResponse(responseCode = "404", description = "없는 대회",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.RACE_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "이미 취소된 대회, 판매가 끝난 대회 또는 그 사이 상태가 바뀜",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "이미 취소된 대회", value = RaceApiExamples.ALREADY_CANCELED),
				@ExampleObject(name = "판매가 끝난 대회", value = RaceApiExamples.CANCEL_AFTER_CLOSED),
				@ExampleObject(name = "그 사이 상태가 바뀜", value = RaceApiExamples.CANCEL_STATUS_CHANGED)
			}))
	})
	public ResponseEntity<RsData<RaceSaveDto>> cancel(
		@LoginMemberId long memberId,
		@Parameter(description = "대회 ID", example = "1")
		@PathVariable long raceId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "대회를 취소했습니다.", productFacade.cancelRace(memberId, raceId)));
	}

	@GetMapping
	@Operation(summary = "공개 대회 목록",
		description = "판매 예정·판매 중이고 아직 접수 마감 전인 대회를 찾는다. 로그인하지 않아도 된다. "
			+ "검색어는 대회명·장소·종목명·지역·소개에서 찾고, 단어는 하나만 맞아도 나온다(예: '충남 트레일'). "
			+ "오타는 단어 길이에 따라 허용한다(1~2글자 단어는 허용 안 함, 3~5글자는 1글자, 6글자 이상은 2글자). "
			+ "정확도순은 대회명에서 맞으면 3배, 장소에서 맞으면 2배 점수를 준다. "
			+ "정렬을 보내지 않으면 검색어가 있을 때 정확도순, 없을 때 마감 임박순이다. "
			+ "종목 조건(종목 구분·노면·거리·참가비)은 종목 하나라도 맞으면 그 대회가 나온다. "
			+ "종목이 여러 개인 대회도 한 줄로 나온다. 가격 낮은순과 최저가(minPrice)는 조건에 맞은 종목 중 가장 싼 종목 기준이다"
			+ "(조건이 없으면 대회에서 가장 싼 종목). 최신 등록순은 대회 등록 일시 기준이다. "
			+ "목록 카드는 대회 정보로 만든 색 포스터를 쓰므로 이미지는 주지 않는다. "
			+ "판매 상태(saleStatus)와 판매 시작일(saleOpenDate)은 판매자 화면용이다. "
			+ "구매자 화면은 크루 예매 기간·개인 예매 오픈·접수 마감 일시로 오픈 예정·예매 중·마감을 보여 준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = RaceApiExamples.SEARCH_SUCCESS))),
		@ApiResponse(responseCode = "400",
			description = "페이지 번호·크기 범위 오류, 너무 큰 페이지 번호, 참가비 범위 오류 "
				+ "또는 값 형식 오류(종목 구분·노면·거리·참가비·정렬·날짜)",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "페이지 크기 초과", value = RaceApiExamples.PAGE_SIZE_EXCEEDED),
				@ExampleObject(name = "너무 큰 페이지 번호", value = RaceApiExamples.PAGE_TOO_LARGE),
				@ExampleObject(name = "참가비 범위 오류", value = RaceApiExamples.PRICE_NEGATIVE),
				@ExampleObject(name = "값 형식 오류", value = RaceApiExamples.INVALID_VALUE_TYPE)
			}))
	})
	public ResponseEntity<RsData<RacePageDto>> search(
		@Parameter(description = "검색어 (대회명·장소·종목명·지역·소개)", example = "서울 10K")
		@RequestParam(required = false) String keyword,
		@Parameter(description = "시·도 (주소 검색 API가 채운 이름 그대로)", example = "서울특별시")
		@RequestParam(required = false) String regionSido,
		@Parameter(description = "시·군·구 (주소 검색 API가 채운 이름 그대로, 이름이 겹치는 곳이 있어 시·도와 함께 보낸다)",
			example = "성동구")
		@RequestParam(required = false) String regionSigungu,
		@Parameter(description = "종목 구분. 여러 개면 courseType=KM_10&courseType=HALF처럼 보낸다", example = "KM_10")
		@RequestParam(name = "courseType", required = false) List<CourseType> courseTypes,
		@Parameter(description = "노면 (ROAD 로드, TRAIL 트레일)", example = "ROAD")
		@RequestParam(required = false) CourseSurface surface,
		@Parameter(description = "거리(km). 이 값과 똑같은 종목만 찾는다", example = "10")
		@RequestParam(required = false) BigDecimal distanceKm,
		@Parameter(description = "참가비 최소 (포함)", example = "30000")
		@RequestParam(required = false) @Min(value = 0, message = "참가비 범위는 0원 이상이어야 합니다.") Long minPrice,
		@Parameter(description = "참가비 최대 (포함)", example = "50000")
		@RequestParam(required = false) @Min(value = 0, message = "참가비 범위는 0원 이상이어야 합니다.") Long maxPrice,
		@Parameter(description = "대회 날짜 시작 (포함)", example = "2026-11-01")
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate raceDateFrom,
		@Parameter(description = "대회 날짜 끝 (포함)", example = "2026-11-30")
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate raceDateTo,
		@Parameter(description = "정렬 (RELEVANCE 정확도순, DEADLINE 마감 임박순, RACE_DATE 대회일 빠른순, "
			+ "LATEST 최신 등록순, PRICE 가격 낮은순). 보내지 않으면 검색어가 있을 때 정확도순, 없을 때 마감 임박순",
			example = "RELEVANCE")
		@RequestParam(required = false) RaceSort sort,
		@Parameter(description = "페이지 번호 (0부터)", example = "0")
		@RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") int page,
		@Parameter(description = "페이지 크기 (1~100)", example = "20")
		@RequestParam(defaultValue = "20") @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
		@Max(value = MAX_PAGE_SIZE, message = "한 번에 100개까지 조회할 수 있습니다.") int size
	) {
		RaceSearchCondition condition = RaceSearchCondition.builder()
			.keyword(keyword)
			.regionSido(regionSido)
			.regionSigungu(regionSigungu)
			.courseTypes(courseTypes)
			.surface(surface)
			.distanceKm(distanceKm)
			.minPrice(minPrice)
			.maxPrice(maxPrice)
			.raceDateFrom(raceDateFrom)
			.raceDateTo(raceDateTo)
			.sort(sort)
			.build();
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "대회 목록을 조회했습니다.", productFacade.searchRaces(condition, page, size)));
	}

	@GetMapping("/suggestions")
	@Operation(summary = "검색창 연관검색어",
		description = "입력한 글자로 시작하는 대회명과 종목명('대회명 종목명')을 각각 5개까지 찾는다. 로그인하지 않아도 된다. "
			+ "공개 목록과 같은 대회(판매 예정·판매 중, 접수 마감 전)에서만 찾는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = RaceApiExamples.SUGGEST_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "검색어 없음",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "검색어 빈 값", value = RaceApiExamples.KEYWORD_BLANK),
				@ExampleObject(name = "검색어 파라미터 없음", value = RaceApiExamples.KEYWORD_MISSING)
			}))
	})
	public ResponseEntity<RsData<RaceSuggestionDto>> suggest(
		@Parameter(description = "검색창에 입력한 글자", example = "서울 하")
		@RequestParam @NotBlank(message = "검색어를 작성해주세요.") String keyword
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "연관검색어를 조회했습니다.", productFacade.suggestRaces(keyword)));
	}

	@GetMapping("/mine")
	@Operation(summary = "판매자 내 대회 목록",
		description = "승인된 판매자가 등록한 대회를 최근 등록 순으로 모두 돌려준다(취소된 대회 포함). "
			+ "종목마다 정원·결제 대기·판매 수를 함께 준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = RaceApiExamples.MINE_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "회원 ID 형식 오류 또는 회원 ID가 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "회원 ID 형식 오류", value = RaceApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = RaceApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "승인된 판매자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.SELLER_NOT_APPROVED)))
	})
	public ResponseEntity<RsData<List<SellerRaceDto>>> findMine(
		@LoginMemberId long memberId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "내 대회 목록을 조회했습니다.", productFacade.findMyRaces(memberId)));
	}

	@GetMapping("/{raceId}")
	@Operation(summary = "대회 상세",
		description = "대회와 종목(남은 자리·티셔츠 사이즈 포함), 판매자 정보를 돌려준다. 로그인하지 않아도 된다. "
			+ "취소된 대회는 등록한 판매자만 볼 수 있고, 다른 사람에게는 없는 대회(404)로 보인다. "
			+ "종목 이미지는 file-service에서 종목마다 받는다(GET /api/v1/file/files?refType=RaceCourse&refId=종목 ID). "
			+ "판매 상태(saleStatus)와 판매 시작일(saleOpenDate)은 판매자 화면용이고, "
			+ "구매자 화면의 예매 버튼은 크루 예매 기간·개인 예매 오픈·접수 마감 일시로 켠다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = RaceApiExamples.DETAIL_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "대회 ID·회원 ID 형식 오류 또는 회원 ID가 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "대회 ID·회원 ID 형식 오류", value = RaceApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = RaceApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "404", description = "없는 대회 또는 다른 판매자의 취소된 대회",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RaceApiExamples.RACE_NOT_FOUND)))
	})
	public ResponseEntity<RsData<RaceDetailDto>> findRace(
		@Parameter(in = ParameterIn.HEADER, name = MemberIdHeader.NAME,
			description = "요청한 회원 ID (로그인했을 때 게이트웨이가 넣어 준다. 취소된 대회는 등록한 판매자만 본다)",
			example = "9001")
		@LoginMemberId(required = false) @Nullable Long memberId,
		@Parameter(description = "대회 ID", example = "1")
		@PathVariable long raceId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "대회를 조회했습니다.", productFacade.findRace(raceId, memberId)));
	}
}
