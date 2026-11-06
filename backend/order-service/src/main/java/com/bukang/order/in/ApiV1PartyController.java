package com.bukang.order.in;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.order.app.PartyFacade;
import com.bukang.order.dto.ParticipationDto;
import com.bukang.order.dto.PartyCreateRequestDto;
import com.bukang.order.dto.PartyCreatedDto;
import com.bukang.order.dto.PartyDto;
import com.bukang.order.dto.PartyJoinRequestDto;
import com.bukang.order.dto.PartyRaceStatusDto;

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

// operationId를 직접 정한다. 메서드 이름(create)이 주문 만들기와 겹치면 프론트 생성 함수 이름이 바뀐다
@Tag(name = "Party", description = "파티 만들기, 참가 링크 다시 만들기, 합류, 나가기 API (BUK-36), 이 대회 내 파티 상태 (BUK-91)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/order/parties")
public class ApiV1PartyController {
	private final PartyFacade partyFacade;

	@PostMapping
	@Operation(operationId = "createParty", summary = "파티 만들기",
		description = "크루원이 파티를 만든다. 파티장이 고른 종목이 파티 종목이 되어 파티원 모두 그 종목으로 신청하고, 만든 뒤에는 바꿀 수 없다. "
			+ "파티장도 참가자라 파티원 신청서(결제 전)를 함께 만들고, 참가 링크(24시간)가 함께 만들어진다. "
			+ "같은 대회에 개인 신청이 남아 있으면 409 ORDER_INDIVIDUAL_PARTICIPATION_EXISTS와 data.participationId를 준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "만들기 성공",
			content = @Content(examples = @ExampleObject(value = PartyApiExamples.CREATE_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패, 제3자 제공 미동의, 요청 본문 형식 오류, 나이 제한 밖 등",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = PartyApiExamples.INVALID_INPUT),
				@ExampleObject(name = "제3자 제공 미동의", value = PartyApiExamples.CONSENT_REQUIRED),
				@ExampleObject(name = "요청 본문 형식 오류", value = PartyApiExamples.INVALID_BODY),
				@ExampleObject(name = "나이 제한 밖", value = PartyApiExamples.AGE_RESTRICTED)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "이 크루의 크루원이 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.CREW_MEMBER_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "없는 종목 또는 없는 크루",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "없는 종목", value = PartyApiExamples.COURSE_NOT_FOUND),
				@ExampleObject(name = "없는 크루", value = PartyApiExamples.CREW_NOT_FOUND)
			})),
		@ApiResponse(responseCode = "409",
			description = "같은 대회에 개인 신청(data.participationId)·다른 파티 신청(data.partyId)이 있음, 취소된 대회, 접수 마감",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "개인 신청 있음", value = PartyApiExamples.INDIVIDUAL_PARTICIPATION_EXISTS),
				@ExampleObject(name = "다른 파티로 신청함", value = PartyApiExamples.OTHER_PARTY_JOINED),
				@ExampleObject(name = "동시에 같은 대회 신청", value = PartyApiExamples.DUPLICATE_PARTICIPATION),
				@ExampleObject(name = "취소된 대회", value = PartyApiExamples.RACE_CANCELED),
				@ExampleObject(name = "접수 마감", value = PartyApiExamples.APPLICATION_CLOSED)
			})),
		@ApiResponse(responseCode = "503", description = "회원·상품 서비스에 연결하지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "회원 서비스", value = PartyApiExamples.MEMBER_SERVICE_UNAVAILABLE),
				@ExampleObject(name = "상품 서비스", value = PartyApiExamples.PRODUCT_SERVICE_UNAVAILABLE)
			}))
	})
	public ResponseEntity<RsData<PartyCreatedDto>> create(
		@LoginMemberId long memberId,
		@Valid @RequestBody PartyCreateRequestDto request
	) {
		PartyCreatedDto created = partyFacade.create(request, memberId);
		return ResponseEntity.created(URI.create("/api/v1/order/parties/" + created.party().id()))
			.body(RsData.of(HttpStatus.CREATED, "파티를 만들었습니다.", created));
	}

	@PostMapping("/{partyId}/join-link")
	@Operation(operationId = "regeneratePartyJoinLink", summary = "참가 링크 다시 만들기",
		description = "파티장만, 모집 중일 때 언제든 다시 만든다. 새로 만들면 옛 링크로는 합류할 수 없다(404).")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "다시 만들기 성공",
			content = @Content(examples = @ExampleObject(value = PartyApiExamples.REGENERATE_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "파티 ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.INVALID_VALUE_TYPE))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "파티장이 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.LEADER_ONLY))),
		@ApiResponse(responseCode = "404", description = "없는 파티",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.PARTY_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "모집 중이 아님 (data.partyId·data.partyStatus)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.NOT_RECRUITING)))
	})
	public ResponseEntity<RsData<PartyDto>> regenerateJoinLink(
		@LoginMemberId long memberId,
		@Parameter(description = "파티 ID", example = "1") @PathVariable long partyId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "참가 링크를 다시 만들었습니다.",
				partyFacade.regenerateJoinLink(partyId, memberId)));
	}

	@PostMapping("/join")
	@Operation(operationId = "joinParty", summary = "참가 링크로 합류",
		description = "그 크루의 크루원이 참가 링크로 들어와 참가자 정보를 넣고 합류를 확정한다. 종목은 고르지 않고 파티 종목으로 "
			+ "파티원 신청서(결제 전)가 만들어진다(나이·티셔츠 확인도 파티 종목 기준). "
			+ "나갔던 사람도 다시 합류할 수 있다. 같은 대회에 개인 신청이 남아 있으면 409 "
			+ "ORDER_INDIVIDUAL_PARTICIPATION_EXISTS와 data.participationId를 준다(개인 신청을 먼저 취소).")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "합류 성공 (data.partyId = 합류한 파티)",
			content = @Content(examples = @ExampleObject(value = PartyApiExamples.JOIN_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패, 제3자 제공 미동의, 요청 본문 형식 오류, 나이 제한 밖 등",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = PartyApiExamples.INVALID_INPUT),
				@ExampleObject(name = "제3자 제공 미동의", value = PartyApiExamples.CONSENT_REQUIRED),
				@ExampleObject(name = "요청 본문 형식 오류", value = PartyApiExamples.INVALID_BODY),
				@ExampleObject(name = "나이 제한 밖", value = PartyApiExamples.AGE_RESTRICTED)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "이 크루의 크루원이 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.CREW_MEMBER_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "참가 링크가 없음(다시 만든 옛 링크 포함) 또는 상품에서 파티 종목이 없어짐",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "링크 없음", value = PartyApiExamples.JOIN_LINK_NOT_FOUND),
				@ExampleObject(name = "없는 종목", value = PartyApiExamples.COURSE_NOT_FOUND)
			})),
		@ApiResponse(responseCode = "409", description = "링크 만료, 모집 중이 아님, 이미 합류함, "
			+ "다른 파티로 신청함, 개인 신청 있음, 취소된 대회, 접수 마감",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "링크 만료", value = PartyApiExamples.LINK_EXPIRED),
				@ExampleObject(name = "모집 중이 아님", value = PartyApiExamples.NOT_RECRUITING),
				@ExampleObject(name = "이미 합류함", value = PartyApiExamples.ALREADY_JOINED),
				@ExampleObject(name = "다른 파티로 신청함", value = PartyApiExamples.OTHER_PARTY_JOINED),
				@ExampleObject(name = "개인 신청 있음", value = PartyApiExamples.INDIVIDUAL_PARTICIPATION_EXISTS),
				@ExampleObject(name = "동시에 같은 대회 신청", value = PartyApiExamples.DUPLICATE_PARTICIPATION),
				@ExampleObject(name = "취소된 대회", value = PartyApiExamples.RACE_CANCELED),
				@ExampleObject(name = "접수 마감", value = PartyApiExamples.APPLICATION_CLOSED)
			})),
		@ApiResponse(responseCode = "503", description = "회원·상품 서비스에 연결하지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "회원 서비스", value = PartyApiExamples.MEMBER_SERVICE_UNAVAILABLE),
				@ExampleObject(name = "상품 서비스", value = PartyApiExamples.PRODUCT_SERVICE_UNAVAILABLE)
			}))
	})
	public ResponseEntity<RsData<ParticipationDto>> join(
		@LoginMemberId long memberId,
		@Valid @RequestBody PartyJoinRequestDto request
	) {
		ParticipationDto participation = partyFacade.join(request, memberId);
		return ResponseEntity.created(URI.create("/api/v1/order/participations/" + participation.getId()))
			.body(RsData.of(HttpStatus.CREATED, "파티에 합류했습니다.", participation));
	}

	@PostMapping("/{partyId}/leave")
	@Operation(operationId = "leaveParty", summary = "파티 나가기",
		description = "신청 전(모집 중)에만 나갈 수 있다. 내 파티원 신청서는 취소되어 그 대회에 다시 신청할 수 있다. "
			+ "파티장이 나가면 가장 먼저 합류한 파티원이 파티장이 되고, 남은 사람이 없으면 파티가 종료된다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "나가기 성공 (취소된 내 신청서)",
			content = @Content(examples = @ExampleObject(value = PartyApiExamples.LEAVE_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "파티 ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.INVALID_VALUE_TYPE))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "이 파티의 파티원이 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.NOT_MEMBER))),
		@ApiResponse(responseCode = "404", description = "없는 파티",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.PARTY_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "모집 중이 아님 (data.partyId·data.partyStatus)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.NOT_RECRUITING)))
	})
	public ResponseEntity<RsData<ParticipationDto>> leave(
		@LoginMemberId long memberId,
		@Parameter(description = "파티 ID", example = "1") @PathVariable long partyId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "파티에서 나갔습니다.", partyFacade.leave(partyId, memberId)));
	}

	@GetMapping("/me/race-status")
	@Operation(operationId = "getMyRacePartyStatus", summary = "이 대회 내 파티 상태",
		description = "대회 상세 화면이 [파티로 신청] 버튼 문구를 고를 때 부른다. 이 대회에서 로그인한 회원이 들어가 있는 파티"
			+ "(종료된 파티·나간 파티 제외)를 찾아 역할·인원·결제 현황을 준다. 파티가 없어도 오류가 아니라 200에 role=NONE을 준다. "
			+ "파티 대기열은 파티장 회원 ID로만 줄을 세우므로 canApply=true(파티장, 모집 중, 접수 중인 대회, 인원이 최소~최대)일 때만 "
			+ "대기열에 넣는다. 안내용이라 최종 판정은 파티 신청 API가 한다(그 사이 바뀌면 409). "
			+ "canApply=false면 reason으로 문구를 고른다. 화면 상태는 role·partyStatus·myPaid로 고른다: "
			+ "LEADER+RECRUITING(모집 중, 인원 모자라면 TOO_FEW_MEMBERS) / MEMBER+RECRUITING(파티원) / "
			+ "LEADER+PAYING(결제 진행 중) / PAYING·PAID에서 myPaid=false(결제할 차례, participationId로 결제)·true(결제 완료) / "
			+ "NONE(파티 없음).")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (파티가 없어도 200)",
			content = @Content(examples = {
				@ExampleObject(name = "파티장·신청 가능", value = PartyApiExamples.LEADER_RECRUITING),
				@ExampleObject(name = "파티장·인원 모자람", value = PartyApiExamples.LEADER_TOO_FEW),
				@ExampleObject(name = "파티원·모집 중", value = PartyApiExamples.MEMBER_RECRUITING),
				@ExampleObject(name = "파티원·결제할 차례", value = PartyApiExamples.MEMBER_PAYING),
				@ExampleObject(name = "파티 없음", value = PartyApiExamples.NO_PARTY)
			})),
		@ApiResponse(responseCode = "400", description = "대회 ID 없음·형식 오류·0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "대회 ID 없음", value = PartyApiExamples.RACE_ID_REQUIRED),
				@ExampleObject(name = "대회 ID 형식 오류", value = PartyApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "대회 ID가 0 이하", value = PartyApiExamples.INVALID_RACE_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.LOGIN_REQUIRED)))
	})
	public ResponseEntity<RsData<PartyRaceStatusDto>> getMyRaceStatus(
		@LoginMemberId long memberId,
		@Parameter(description = "대회 ID", example = "100")
		@RequestParam @Positive(message = "대회 ID가 올바르지 않습니다.") long raceId
	) {
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "이 대회 내 파티 상태를 조회했습니다.",
			partyFacade.findMyRaceStatus(raceId, memberId)));
	}
}
