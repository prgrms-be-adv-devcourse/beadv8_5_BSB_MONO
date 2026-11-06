package com.bukang.order.in;

import java.time.LocalDateTime;
import java.util.List;

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
import com.bukang.order.app.PartyQueryFacade;
import com.bukang.order.dto.PartyDetailDto;
import com.bukang.order.dto.PartyPreviewDto;
import com.bukang.order.dto.PartyPreviewRequestDto;
import com.bukang.order.dto.PartySummaryDto;

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

// 파티 조회 (BUK-39). 만들기·합류 컨트롤러(ApiV1PartyController)와 나눠, 그쪽을 고치는 작업과 겹치지 않게 한다
@Tag(name = "Party", description = "파티 만들기, 참가 링크 다시 만들기, 합류, 나가기 API (BUK-36), 파티 조회 (BUK-39)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/order/parties")
public class ApiV1PartyQueryController {
	private final PartyQueryFacade partyQueryFacade;

	@GetMapping("/{partyId}")
	@Operation(operationId = "getParty", summary = "파티 상세",
		description = "파티원만 본다. 파티원 닉네임·합류 시각·신청서 상태·결제 여부와 인원·결제 인원을 준다. "
			+ "생년월일·연락처 같은 참가자 정보는 주지 않는다. 참가 링크 토큰은 파티장에게만 주고 파티원에게는 null이다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = PartyQueryApiExamples.DETAIL_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "파티 ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.INVALID_VALUE_TYPE))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "이 파티의 파티원이 아님 (나간 사람 포함)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.NOT_MEMBER))),
		@ApiResponse(responseCode = "404", description = "없는 파티",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.PARTY_NOT_FOUND))),
		@ApiResponse(responseCode = "503", description = "회원 서비스(닉네임)에 연결하지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.MEMBER_SERVICE_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<PartyDetailDto>> getParty(
		@LoginMemberId long memberId,
		@Parameter(description = "파티 ID", example = "1") @PathVariable long partyId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "파티를 조회했습니다.", partyQueryFacade.find(partyId, memberId)));
	}

	@GetMapping("/me")
	@Operation(operationId = "getMyParties", summary = "내 파티 목록",
		description = "내가 지금 들어 있는 파티를 최근 순으로 준다(나간 파티는 빠짐). "
			+ "raceId를 주면 그 대회의 파티만 준다(대회 상세에서 「내 파티 보기」를 띄울 때). 참가 링크 토큰은 넣지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (없으면 빈 목록)",
			content = @Content(examples = @ExampleObject(value = PartyQueryApiExamples.MY_PARTIES_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "대회 ID 형식 오류·0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.INVALID_VALUE_TYPE))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "503", description = "회원 서비스(닉네임)에 연결하지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.MEMBER_SERVICE_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<List<PartySummaryDto>>> getMyParties(
		@LoginMemberId long memberId,
		@Parameter(description = "대회 ID (없으면 모든 대회)", example = "100")
		@RequestParam(required = false) @Positive(message = "대회 ID가 올바르지 않습니다.") Long raceId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "내 파티를 조회했습니다.", partyQueryFacade.findMine(memberId, raceId)));
	}

	@PostMapping("/preview")
	@Operation(operationId = "previewParty", summary = "참가 링크로 파티 미리 보기",
		description = "참가 링크를 연 순간 부른다(시안 08 합류 확인 위쪽). 로그인한 누구나 볼 수 있고 크루원 확인은 합류 때 한다. "
			+ "토큰이 없거나 다시 만든 옛 토큰이면 404다. 그 밖에는 200에 파티 상태와 링크가 살아 있는지를 준다. "
			+ "status가 RECRUITING이 아니면 「모집 끝남」, joinLinkAlive=false면 「링크 끝남」 화면으로 보낸다. "
			+ "토큰은 주소에 남지 않게 본문으로 받는다. 파티원 명단·참가자 정보는 주지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (링크가 끝났거나 모집이 끝나도 200)",
			content = @Content(examples = @ExampleObject(value = PartyQueryApiExamples.PREVIEW_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "토큰 없음·64자 넘음, 요청 본문 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "토큰 없음", value = PartyQueryApiExamples.PREVIEW_INVALID_INPUT),
				@ExampleObject(name = "요청 본문 형식 오류", value = PartyApiExamples.INVALID_BODY)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "참가 링크가 없음 (다시 만든 옛 링크 포함)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.JOIN_LINK_NOT_FOUND))),
		@ApiResponse(responseCode = "503", description = "회원 서비스(닉네임)에 연결하지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.MEMBER_SERVICE_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<PartyPreviewDto>> previewParty(
		@LoginMemberId long memberId,
		@Valid @RequestBody PartyPreviewRequestDto request
	) {
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "참가 링크의 파티를 조회했습니다.",
			partyQueryFacade.preview(request.joinToken(), LocalDateTime.now())));
	}

	@GetMapping("/crews/{crewId}/active")
	@Operation(operationId = "getCrewActiveParties", summary = "크루의 진행 중인 파티",
		description = "크루 홈 「진행 중인 파티」(시안 19·M09). 그 크루의 크루원에게 모집 중·결제 진행 중인 파티를 모두 최근 순으로 준다 "
			+ "(11/05 회의 P7: 합류는 링크로만). myParty=true인 카드만 상세로 보낸다. 참가 링크 토큰은 넣지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (없으면 빈 목록)",
			content = @Content(examples = @ExampleObject(value = PartyQueryApiExamples.CREW_ACTIVE_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "크루 ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.INVALID_VALUE_TYPE))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "이 크루의 크루원이 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.CREW_MEMBER_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "없는 크루",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.CREW_NOT_FOUND))),
		@ApiResponse(responseCode = "503", description = "회원 서비스(크루·닉네임)에 연결하지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PartyApiExamples.MEMBER_SERVICE_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<List<PartySummaryDto>>> getCrewActiveParties(
		@LoginMemberId long memberId,
		@Parameter(description = "크루 ID", example = "7") @PathVariable long crewId
	) {
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "크루의 진행 중인 파티를 조회했습니다.",
			partyQueryFacade.findActiveInCrew(crewId, memberId)));
	}
}
