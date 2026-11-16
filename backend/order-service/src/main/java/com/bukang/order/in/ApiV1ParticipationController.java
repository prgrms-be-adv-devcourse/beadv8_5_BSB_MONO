package com.bukang.order.in;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.order.app.MyTicketQueryFacade;
import com.bukang.order.app.ParticipationFacade;
import com.bukang.order.dto.KitOptionChangeRequestDto;
import com.bukang.order.dto.MyTicketPageDto;
import com.bukang.order.dto.ParticipantInfoDto;
import com.bukang.order.dto.ParticipantInfoRequestDto;
import com.bukang.order.dto.ParticipationApplyRequestDto;
import com.bukang.order.dto.ParticipationDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@Tag(name = "Participation", description = "참가 신청서 작성·취소, 참가 신청 조회, 내 참가권 목록, 참가자 정보·키트 옵션 수정, "
	+ "최근 참가자 정보 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/order/participations")
public class ApiV1ParticipationController {
	// 한 페이지에 다른 종목이 많으면 상품 서비스를 그만큼 부르므로 주문 목록(100)보다 작게 둔다
	private static final int MAX_TICKET_PAGE_SIZE = 50;

	private final ParticipationFacade participationFacade;
	private final MyTicketQueryFacade myTicketQueryFacade;

	// ResponseEntity.created()의 201은 springdoc이 추론하지 못하므로 응답 코드를 직접 명시한다
	// 에러 응답은 GlobalExceptionHandler, OrderExceptionHandler가 RsData(data: null) 형식으로 반환한다
	@PostMapping
	@Operation(summary = "참가 신청서 작성",
		description = "결제 전에 참가자 정보와 개인정보 제3자 제공 동의를 받아 참가 신청을 자리 확보 전(PENDING) 상태로 저장한다. "
			+ "종목의 접수 기간, 대회 당일 만 나이 제한, 같은 대회에 살아 있는 신청이 있는지(한 사람 한 대회 1건)를 확인한다. "
			+ "같은 대회에 결제 전(PENDING) 신청서가 남아 있으면 그 신청서를 취소하고 새로 저장한다(다른 종목이어도). "
			+ "결제 대기·결제 완료 신청이 있으면 409다. "
			+ "키트가 있는 종목은 배송 주소가 필수이고, 키트가 없는 종목은 배송 주소를 저장하지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "저장 성공",
			content = @Content(examples = @ExampleObject(value = ParticipationApiExamples.APPLY_SUCCESS))),
		@ApiResponse(responseCode = "400",
			description = "입력값 검증 실패, 제3자 제공 미동의, 요청 본문 형식 오류, 나이 제한 밖, 키트 종목의 배송 주소 없음, "
				+ "티셔츠 사이즈 없음·고를 수 없는 사이즈 또는 회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = ParticipationApiExamples.INVALID_INPUT),
				@ExampleObject(name = "제3자 제공 미동의", value = ParticipationApiExamples.CONSENT_REQUIRED),
				@ExampleObject(name = "요청 본문 형식 오류", value = ParticipationApiExamples.INVALID_BODY),
				@ExampleObject(name = "나이 제한 밖", value = ParticipationApiExamples.AGE_RESTRICTED),
				@ExampleObject(name = "배송 주소 없음", value = ParticipationApiExamples.SHIPPING_ADDRESS_REQUIRED),
				@ExampleObject(name = "티셔츠 사이즈 없음", value = ParticipationApiExamples.TSHIRT_SIZE_REQUIRED),
				@ExampleObject(name = "고를 수 없는 티셔츠 사이즈",
					value = ParticipationApiExamples.TSHIRT_SIZE_NOT_AVAILABLE),
				@ExampleObject(name = "회원 ID 형식 오류", value = ParticipationApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = ParticipationApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = ParticipationApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "없는 종목",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = ParticipationApiExamples.COURSE_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "같은 대회에 결제 대기·결제 완료 신청이 있음, "
			+ "같은 대회 파티에 들어가 있음(data에 파티 번호·상태, 파티를 나간 뒤 신청), 접수 기간이 아님, "
			+ "남은 결제 전 신청서를 [결제하기]가 처리 중이라 잠시 뒤 다시 시도",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "이미 신청함", value = ParticipationApiExamples.DUPLICATE_PARTICIPATION),
				@ExampleObject(name = "파티에 들어가 있음", value = ParticipationApiExamples.PARTY_PARTICIPATION_EXISTS),
				@ExampleObject(name = "접수 기간 아님", value = ParticipationApiExamples.NOT_ACCEPTING),
				@ExampleObject(name = "잠시 뒤 다시 시도", value = ParticipationApiExamples.APPLY_RETRY)
			}))
	})
	public ResponseEntity<RsData<ParticipationDto>> apply(
		@LoginMemberId long memberId,
		@Valid @RequestBody ParticipationApplyRequestDto request
	) {
		ParticipationDto participation = participationFacade.apply(request, memberId);
		return ResponseEntity.created(URI.create("/api/v1/order/participations/" + participation.getId()))
			.body(RsData.of(HttpStatus.CREATED, "참가 신청서를 저장했습니다.", participation));
	}

	@GetMapping("/latest-participant")
	@Operation(summary = "최근 참가자 정보 조회",
		description = "다음 신청서에 미리 채울 수 있도록, 회원이 가장 최근에 작성한 참가 신청의 참가자 정보를 돌려준다. "
			+ "그 신청에 배송 주소가 없으면(키트 없는 종목) 주소를 적은 최근 신청의 주소를 채운다. "
			+ "신청한 적이 없으면 data가 null이다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (이전 신청이 없으면 data가 null)",
			content = @Content(examples = {
				@ExampleObject(name = "이전 신청 있음", value = ParticipationApiExamples.LATEST_PARTICIPANT_SUCCESS),
				@ExampleObject(name = "이전 신청 없음", value = ParticipationApiExamples.LATEST_PARTICIPANT_EMPTY)
			})),
		@ApiResponse(responseCode = "400", description = "회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "회원 ID 형식 오류", value = ParticipationApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = ParticipationApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = ParticipationApiExamples.LOGIN_REQUIRED)))
	})
	public ResponseEntity<RsData<ParticipantInfoDto>> findLatestParticipant(
		@LoginMemberId long memberId
	) {
		return participationFacade.findLatestParticipant(memberId)
			.map(participant -> ResponseEntity.ok()
				.body(RsData.of(HttpStatus.OK, "최근 참가자 정보를 조회했습니다.", participant)))
			.orElseGet(() -> ResponseEntity.ok()
				.body(RsData.of(HttpStatus.OK, "이전에 신청한 참가자 정보가 없습니다.", null)));
	}

	@GetMapping("/me")
	@Operation(summary = "내 참가권 목록",
		description = "내 참가 신청을 최신순으로 돌려준다. 신청 1건이 한 줄이고, 결제 전 신청과 주문이 아직 없는 파티원 신청도 나온다. "
			+ "한 줄에 대회·종목·대회일·환불 기한, 참가자, 신청·주문 상태, 고른 키트 옵션, 키트 배송 상태·택배사·송장, "
			+ "파티(크루 ID·파티장)를 담는다. active=true면 살아 있는 신청(확보 전·결제 대기·참가 확정)만 준다. "
			+ "대회 이름·환불 기한은 상품 서비스에서 받으므로, 상품 서비스에 묻지 못하면 그 칸만 null이고 "
			+ "종목 이름·대회일은 주문 당시 값으로 채운다. 파티장 닉네임도 회원 서비스에 묻지 못하면 null이다. "
			+ "크루 이름은 담지 않으니 GET /api/v1/member/crews/me의 크루 ID로 찾는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (신청이 없으면 빈 목록)",
			content = @Content(examples = @ExampleObject(value = ParticipationApiExamples.MY_TICKETS_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "페이지·회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "페이지 크기 초과", value = ParticipationApiExamples.INVALID_TICKET_PAGE),
				@ExampleObject(name = "숫자 형식 오류", value = ParticipationApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = ParticipationApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = ParticipationApiExamples.LOGIN_REQUIRED)))
	})
	public ResponseEntity<RsData<MyTicketPageDto>> findMyTickets(
		@LoginMemberId long memberId,
		@Parameter(description = "true면 살아 있는 신청(확보 전·결제 대기·참가 확정)만", example = "false")
		@RequestParam(defaultValue = "false") boolean active,
		@Parameter(description = "페이지 번호 (0부터)", example = "0")
		@RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") int page,
		@Parameter(description = "페이지 크기 (1~50)", example = "20")
		@RequestParam(defaultValue = "20") @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
		@Max(value = MAX_TICKET_PAGE_SIZE, message = "한 번에 50개까지 조회할 수 있습니다.") int size
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "내 참가권 목록을 조회했습니다.",
				myTicketQueryFacade.findMine(memberId, active, page, size)));
	}

	@GetMapping("/{participationId}")
	@Operation(summary = "참가 신청 조회",
		description = "본인의 참가 신청과 참가자 정보, 상태, 결제 때 고른 키트 옵션(kitOptions)을 돌려준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = ParticipationApiExamples.FIND_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "참가 신청 ID·회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "참가 신청 ID·회원 ID 형식 오류", value = ParticipationApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = ParticipationApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = ParticipationApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "다른 회원의 참가 신청",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = ParticipationApiExamples.NOT_OWNER))),
		@ApiResponse(responseCode = "404", description = "없는 참가 신청",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = ParticipationApiExamples.PARTICIPATION_NOT_FOUND)))
	})
	public ResponseEntity<RsData<ParticipationDto>> findById(
		@LoginMemberId long memberId,
		@Parameter(description = "참가 신청 ID", example = "1")
		@PathVariable long participationId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "참가 신청을 조회했습니다.",
				participationFacade.findById(participationId, memberId)));
	}

	@PostMapping("/{participationId}/cancel")
	@Operation(summary = "참가 신청서 취소",
		description = "[결제하기] 전(PENDING)인 본인 참가 신청서를 취소한다. 취소하면 같은 대회의 다른 종목으로 다시 신청할 수 있다. "
			+ "결제 대기 중인 신청은 결제 기한이 지나면 자동으로 풀리고, 결제한 참가권은 주문 상품 취소로 취소한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "취소 성공",
			content = @Content(examples = @ExampleObject(value = ParticipationApiExamples.CANCEL_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "참가 신청 ID·회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "참가 신청 ID·회원 ID 형식 오류", value = ParticipationApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = ParticipationApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = ParticipationApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "다른 회원의 참가 신청",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = ParticipationApiExamples.NOT_OWNER))),
		@ApiResponse(responseCode = "404", description = "없는 참가 신청",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = ParticipationApiExamples.PARTICIPATION_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "결제 대기 중, 결제 완료, 이미 끝난 신청 또는 파티원 신청서(파티 나가기로 취소)",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "결제 대기 중", value = ParticipationApiExamples.NOT_CANCELABLE_APPLIED),
				@ExampleObject(name = "결제 완료", value = ParticipationApiExamples.NOT_CANCELABLE_CONFIRMED),
				@ExampleObject(name = "이미 끝난 신청", value = ParticipationApiExamples.NOT_CANCELABLE_FINISHED),
				@ExampleObject(name = "파티원 신청서", value = ParticipationApiExamples.NOT_CANCELABLE_PARTY)
			}))
	})
	public ResponseEntity<RsData<ParticipationDto>> cancelBeforePayment(
		@LoginMemberId long memberId,
		@Parameter(description = "참가 신청 ID", example = "1")
		@PathVariable long participationId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "참가 신청서를 취소했습니다.",
				participationFacade.cancelBeforePayment(participationId, memberId)));
	}

	@PutMapping("/{participationId}/participant")
	@Operation(summary = "참가자 정보 수정",
		description = "접수 마감 전까지 본인 참가 신청의 참가자 정보를 통째로 바꾼다. 생년월일이 바뀔 수 있어 나이 제한을 다시 확인한다. "
			+ "키트가 있는 종목은 배송 주소가 필수이고, 키트가 없는 종목은 배송 주소를 저장하지 않는다. "
			+ "취소·미결제 해제·확보 실패한 신청은 고칠 수 없다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "수정 성공",
			content = @Content(examples = @ExampleObject(value = ParticipationApiExamples.CHANGE_SUCCESS))),
		@ApiResponse(responseCode = "400",
			description = "입력값 검증 실패, 요청 본문 형식 오류, 나이 제한 밖, 키트 종목의 배송 주소 없음, "
				+ "티셔츠 사이즈 없음·고를 수 없는 사이즈 또는 참가 신청 ID·회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = ParticipationApiExamples.INVALID_INPUT),
				@ExampleObject(name = "요청 본문 형식 오류", value = ParticipationApiExamples.INVALID_BODY),
				@ExampleObject(name = "나이 제한 밖", value = ParticipationApiExamples.AGE_RESTRICTED),
				@ExampleObject(name = "배송 주소 없음", value = ParticipationApiExamples.SHIPPING_ADDRESS_REQUIRED),
				@ExampleObject(name = "티셔츠 사이즈 없음", value = ParticipationApiExamples.TSHIRT_SIZE_REQUIRED),
				@ExampleObject(name = "고를 수 없는 티셔츠 사이즈",
					value = ParticipationApiExamples.TSHIRT_SIZE_NOT_AVAILABLE),
				@ExampleObject(name = "참가 신청 ID·회원 ID 형식 오류", value = ParticipationApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = ParticipationApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = ParticipationApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "다른 회원의 참가 신청",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = ParticipationApiExamples.NOT_OWNER))),
		@ApiResponse(responseCode = "404", description = "없는 참가 신청 또는 없는 종목",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "없는 참가 신청", value = ParticipationApiExamples.PARTICIPATION_NOT_FOUND),
				@ExampleObject(name = "없는 종목", value = ParticipationApiExamples.COURSE_NOT_FOUND)
			})),
		@ApiResponse(responseCode = "409", description = "접수 마감 뒤, 고칠 수 없는 상태 또는 수정 중 주문이 만들어짐(다시 시도)",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "접수 마감 뒤", value = ParticipationApiExamples.APPLICATION_CLOSED),
				@ExampleObject(name = "고칠 수 없는 상태", value = ParticipationApiExamples.NOT_EDITABLE),
				@ExampleObject(name = "수정 중 주문이 만들어짐", value = ParticipationApiExamples.CHANGE_RETRY)
			}))
	})
	public ResponseEntity<RsData<ParticipationDto>> changeParticipant(
		@LoginMemberId long memberId,
		@Parameter(description = "참가 신청 ID", example = "1")
		@PathVariable long participationId,
		@Valid @RequestBody ParticipantInfoRequestDto request
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "참가자 정보를 수정했습니다.",
				participationFacade.changeParticipant(participationId, request, memberId)));
	}

	@PutMapping("/{participationId}/kit-options")
	@Operation(summary = "키트 옵션 바꾸기",
		description = "결제한 본인 참가권의 키트 옵션(예: 티셔츠 M → L)을 접수 마감 전까지 바꾼다. 결제 때처럼 바꾼 뒤의 옵션을 "
			+ "모두 보내고, 옵션 이름마다 하나씩 골라야 한다. 옛 옵션의 판매 수는 1 줄고 새 옵션의 판매 수는 1 는다. "
			+ "새 옵션이 준비된 수량만큼 팔렸으면 409(data.soldOut, 결제 품절과 같은 모양)이고 아무것도 바뀌지 않는다. "
			+ "바꾼 옵션은 내 참가권 목록과 판매자 키트 목록에 바로 보인다. 결제 전 신청·취소 중·취소된 참가권은 바꿀 수 없다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "바꾸기 성공 (같은 옵션을 보내면 그대로 200)",
			content = @Content(examples = @ExampleObject(value = ParticipationApiExamples.KIT_OPTION_CHANGE_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패, 요청 본문 형식 오류, 고른 옵션이 대회 옵션과 맞지 않음 "
			+ "또는 참가 신청 ID·회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "옵션 ID 형식 오류", value = ParticipationApiExamples.INVALID_KIT_OPTION_ID),
				@ExampleObject(name = "요청 본문 형식 오류", value = ParticipationApiExamples.INVALID_BODY),
				@ExampleObject(name = "이 대회 옵션이 아님", value = ParticipationApiExamples.KIT_OPTION_NOT_IN_RACE),
				@ExampleObject(name = "고르지 않은 옵션 이름", value = ParticipationApiExamples.KIT_OPTION_MISSING),
				@ExampleObject(name = "한 이름에서 둘을 고름", value = ParticipationApiExamples.KIT_OPTION_DUPLICATED_NAME),
				@ExampleObject(name = "참가 신청 ID·회원 ID 형식 오류", value = ParticipationApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = ParticipationApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = ParticipationApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "다른 회원의 참가 신청",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = ParticipationApiExamples.NOT_OWNER))),
		@ApiResponse(responseCode = "404", description = "없는 참가 신청 또는 없는 종목",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "없는 참가 신청", value = ParticipationApiExamples.PARTICIPATION_NOT_FOUND),
				@ExampleObject(name = "없는 종목", value = ParticipationApiExamples.COURSE_NOT_FOUND)
			})),
		@ApiResponse(responseCode = "409", description = "접수 마감 뒤, 취소된 대회, 바꿀 수 없는 참가권 또는 새 옵션 품절",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "접수 마감 뒤", value = ParticipationApiExamples.KIT_OPTION_APPLICATION_CLOSED),
				@ExampleObject(name = "취소된 대회", value = ParticipationApiExamples.RACE_CANCELED),
				@ExampleObject(name = "결제 전 참가 신청", value = ParticipationApiExamples.KIT_OPTION_NOT_CONFIRMED),
				@ExampleObject(name = "취소 처리 중", value = ParticipationApiExamples.KIT_OPTION_CANCELING),
				@ExampleObject(name = "새 옵션 품절", value = ParticipationApiExamples.KIT_OPTION_SOLD_OUT)
			})),
		@ApiResponse(responseCode = "503", description = "상품 서비스에서 키트 옵션을 받지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = ParticipationApiExamples.KIT_OPTIONS_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<ParticipationDto>> changeKitOptions(
		@LoginMemberId long memberId,
		@Parameter(description = "참가 신청 ID", example = "1")
		@PathVariable long participationId,
		@Valid @RequestBody KitOptionChangeRequestDto request
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "키트 옵션을 바꿨습니다.",
				participationFacade.changeKitOptions(participationId, request.getKitOptionIds(), memberId)));
	}
}
