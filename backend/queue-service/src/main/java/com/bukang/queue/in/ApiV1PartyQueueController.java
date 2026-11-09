package com.bukang.queue.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.queue.app.QueueFacade;
import com.bukang.queue.dto.QueueRegisterRequestDto;
import com.bukang.queue.dto.QueueStatusDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

/**
 * 파티 대기열 (크루 예매 기간에 파티장이 선다). 개인 대기열(ApiV1QueueController)과 줄이 따로라 서로 순번에 영향을 주지 않는다.
 * 줄에는 파티장 회원 ID(X-Member-Id)가 선다. 파티 ID는 받지 않고, 파티장인지도 확인하지 않는다
 * (프론트가 등록 전에 주문 서비스의 파티장 확인 API로 막고, 주문 서비스가 [대회 참가] 때 다시 확인한다).
 */
@Tag(name = "PartyQueue", description = "파티 대기열 등록, 순번 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/queue/parties")
public class ApiV1PartyQueueController {
	private final QueueFacade queueFacade;

	// 에러 응답은 GlobalExceptionHandler, QueueExceptionHandler가 RsData(data: null) 형식으로 반환한다
	@PostMapping
	@Operation(summary = "파티 대기열 등록",
		description = "대회·종목의 파티 대기열에 파티장을 줄 세우고 순번을 돌려준다. "
			+ "판매 중이고 크루 예매 시작부터 크루 예매 마감 전까지(접수 마감 전)만 들어올 수 있다. "
			+ "등록 전에 주문 서비스의 파티장 확인 API(GET /api/v1/order/parties/leader?raceId=)로 파티장인지 확인하고, "
			+ "종목은 그 응답의 파티 종목을 보낸다. 대기열은 파티장인지 확인하지 않는다. "
			+ "이미 줄에 있는 파티장이 다시 등록하면(다른 기기 등) 처음 순번을 그대로 돌려준다. "
			+ "이미 입장(active)한 파티장은 다시 줄을 설 수 없다. "
			+ "대기열(Redis)에 연결하지 못하면 대기열 없이 통과시키지 않고 막는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "등록 성공 (이미 줄에 있으면 처음 순번)",
			content = @Content(examples = @ExampleObject(value = PartyQueueApiExamples.REGISTER_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패, 요청 본문 형식 오류 또는 회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = QueueApiExamples.INVALID_INPUT),
				@ExampleObject(name = "요청 본문 형식 오류", value = QueueApiExamples.INVALID_BODY),
				@ExampleObject(name = "회원 ID 형식 오류", value = QueueApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = QueueApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = QueueApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403",
			description = "다른 대회의 종목 또는 대회에서 뺀 종목, 판매를 멈춘 대회, 크루 예매 기간이 아님"
				+ "(판매 중이 아님, 크루 예매가 없는 대회, 크루 예매 시작 전, 크루 예매 마감·접수 마감 뒤)",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "이 대회의 종목이 아님", value = QueueApiExamples.COURSE_NOT_IN_RACE),
				@ExampleObject(name = "판매를 멈춘 대회", value = QueueApiExamples.SALE_STOPPED),
				@ExampleObject(name = "크루 예매 기간 아님", value = PartyQueueApiExamples.NOT_CREW_SALE_PERIOD)
			})),
		@ApiResponse(responseCode = "404", description = "없는 대회 또는 없는 종목",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "없는 대회", value = QueueApiExamples.RACE_NOT_FOUND),
				@ExampleObject(name = "없는 종목", value = QueueApiExamples.COURSE_NOT_FOUND)
			})),
		@ApiResponse(responseCode = "409", description = "이미 입장(active)한 파티장",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = QueueApiExamples.ALREADY_ACTIVE))),
		@ApiResponse(responseCode = "503", description = "대기열(Redis) 연결 실패",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = QueueApiExamples.QUEUE_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<QueueStatusDto>> register(
		@LoginMemberId long leaderMemberId,
		@Valid @RequestBody QueueRegisterRequestDto request
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "파티 대기열에 등록했습니다.",
				queueFacade.registerParty(leaderMemberId, request.getRaceId(), request.getRaceCourseId())));
	}

	@GetMapping("/races/{raceId}/racecourses/{raceCourseId}")
	@Operation(summary = "파티 대기열 순번 조회",
		description = "파티 대기 화면이 몇 초마다 불러 파티장의 순번과 입장 여부를 확인한다. "
			+ "줄에서 기다리는 중이면 순번·앞 인원·전체 인원을, 입장(active)했으면 canEnter=true를 돌려준다. "
			+ "canEnter=true가 되면 [대회 참가] 화면으로 넘어간다. "
			+ "대기열(Redis)만 읽고 대회·종목이 있는지는 확인하지 않는다. "
			+ "대기열(Redis)에 연결하지 못하면 막는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (기다리는 중 또는 입장 가능)",
			content = @Content(examples = {
				@ExampleObject(name = "기다리는 중", value = PartyQueueApiExamples.STATUS_WAITING),
				@ExampleObject(name = "입장 가능", value = PartyQueueApiExamples.STATUS_CAN_ENTER)
			})),
		@ApiResponse(responseCode = "400", description = "대회·종목 ID 또는 회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "ID 형식 오류", value = QueueApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "대회·종목 ID가 0 이하", value = QueueApiExamples.INVALID_RACE_ID),
				@ExampleObject(name = "회원 ID가 0 이하", value = QueueApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = QueueApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404",
			description = "파티 줄에도 없고 입장한 상태도 아닌 회원 (등록하지 않음, 입장 시간이 끝남, 없는 대회·종목)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = QueueApiExamples.MEMBER_NOT_IN_QUEUE))),
		@ApiResponse(responseCode = "503", description = "대기열(Redis) 연결 실패",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = QueueApiExamples.QUEUE_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<QueueStatusDto>> getStatus(
		@LoginMemberId long leaderMemberId,
		@Parameter(description = "대회 ID", example = "1")
		@PathVariable @Positive(message = "대회 ID는 0보다 커야 합니다.") long raceId,
		@Parameter(description = "종목 ID (파티 종목)", example = "1")
		@PathVariable @Positive(message = "종목 ID는 0보다 커야 합니다.") long raceCourseId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "파티 대기열 상태를 조회했습니다.",
				queueFacade.getPartyStatus(leaderMemberId, raceId, raceCourseId)));
	}
}
