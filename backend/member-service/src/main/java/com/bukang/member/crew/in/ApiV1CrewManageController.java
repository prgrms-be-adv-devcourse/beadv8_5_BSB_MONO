package com.bukang.member.crew.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.member.crew.app.CrewFacade;
import com.bukang.member.crew.dto.CrewHomeDto;
import com.bukang.member.crew.dto.CrewLeaderTransferRequestDto;
import com.bukang.member.crew.dto.CrewUpdateRequestDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 크루 관리 (2그룹, BE-032). 크루원을 내보내거나 크루를 바꾸는 일은 크루장만 한다
@Tag(name = "CrewManage", description = "크루 탈퇴, 크루원 내보내기, 크루장 넘기기, 크루 없애기, 크루 이름 바꾸기 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/member/crews")
public class ApiV1CrewManageController {
	private final CrewFacade crewFacade;

	@DeleteMapping("/{crewId}/members/me")
	@Operation(operationId = "leaveCrew", summary = "크루 탈퇴 (2그룹)",
		description = "크루원과 운영진이 스스로 크루를 떠난다. 크루장은 크루장을 넘긴 뒤에만 탈퇴한다. "
			+ "이미 결제한 참가권은 남고, 결제하지 않고 임시로 잡아 둔 자리만 풀린다(파티 서비스가 처리).")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "탈퇴 성공",
			content = @Content(examples = @ExampleObject(value = CrewApiExamples.LEAVE_SUCCESS))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "크루원이 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.CREW_NOT_MEMBER))),
		@ApiResponse(responseCode = "404", description = "크루가 없거나 없앤 크루",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.CREW_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "크루장은 바로 탈퇴할 수 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.CREW_LEADER_CANNOT_LEAVE)))
	})
	public ResponseEntity<RsData<Void>> leaveCrew(
		@LoginMemberId long memberId,
		@Parameter(description = "크루 ID", example = "1") @PathVariable long crewId
	) {
		crewFacade.leave(memberId, crewId);
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "크루를 탈퇴했습니다.", null));
	}

	@DeleteMapping("/{crewId}/members/{memberId}")
	@Operation(operationId = "removeCrewMember", summary = "크루원 내보내기 (2그룹)",
		description = "크루장이 크루원이나 운영진을 크루에서 내보낸다. 운영진은 내보낼 수 없다(크루장만 한다). "
			+ "크루장 자신은 내보낼 수 없다. 결제 기간 중인 파티장·파티원을 막을지는 회의에서 정한다(안건 14).")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "내보내기 성공",
			content = @Content(examples = @ExampleObject(value = CrewApiExamples.REMOVE_MEMBER_SUCCESS))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "크루원이 아니거나 크루장이 아님",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "크루원이 아님", value = CrewApiExamples.CREW_NOT_MEMBER),
				@ExampleObject(name = "크루장이 아님", value = CrewApiExamples.CREW_LEADER_ONLY)
			})),
		@ApiResponse(responseCode = "404", description = "크루가 없거나 대상이 크루원이 아님",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "크루 없음", value = CrewApiExamples.CREW_NOT_FOUND),
				@ExampleObject(name = "대상이 크루원이 아님", value = CrewApiExamples.CREW_MEMBER_NOT_FOUND)
			})),
		@ApiResponse(responseCode = "409", description = "크루장 자신을 내보내려 함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.CREW_CANNOT_REMOVE_LEADER)))
	})
	public ResponseEntity<RsData<Void>> removeCrewMember(
		@LoginMemberId long loginMemberId,
		@Parameter(description = "크루 ID", example = "1") @PathVariable long crewId,
		@Parameter(description = "내보낼 크루원의 회원 ID", example = "2") @PathVariable long memberId
	) {
		crewFacade.removeMember(loginMemberId, crewId, memberId);
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "크루원을 내보냈습니다.", null));
	}

	@PutMapping("/{crewId}/leader")
	@Operation(operationId = "transferCrewLeader", summary = "크루장 넘기기 (2그룹)",
		description = "크루장이 다른 크루원에게 크루장을 넘긴다. 넘긴 사람은 크루원 역할이 된다. 넘겨받은 사람의 가입 시각은 바뀌지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "넘기기 성공",
			content = @Content(examples = @ExampleObject(value = CrewApiExamples.TRANSFER_LEADER_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 요청 본문 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = CrewApiExamples.TRANSFER_INVALID_INPUT),
				@ExampleObject(name = "요청 본문 형식 오류", value = CrewApiExamples.INVALID_BODY)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "크루원이 아니거나 크루장이 아님",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "크루원이 아님", value = CrewApiExamples.CREW_NOT_MEMBER),
				@ExampleObject(name = "크루장이 아님", value = CrewApiExamples.CREW_LEADER_ONLY)
			})),
		@ApiResponse(responseCode = "404", description = "크루가 없거나 대상이 크루원이 아님",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "크루 없음", value = CrewApiExamples.CREW_NOT_FOUND),
				@ExampleObject(name = "대상이 크루원이 아님", value = CrewApiExamples.CREW_MEMBER_NOT_FOUND)
			})),
		@ApiResponse(responseCode = "409", description = "자기 자신에게 넘기려 함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.CREW_ALREADY_LEADER)))
	})
	public ResponseEntity<RsData<CrewHomeDto>> transferCrewLeader(
		@LoginMemberId long memberId,
		@Parameter(description = "크루 ID", example = "1") @PathVariable long crewId,
		@Valid @RequestBody CrewLeaderTransferRequestDto request
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "크루장을 넘겼습니다.",
				crewFacade.transferLeader(memberId, crewId, request.getMemberId())));
	}

	@DeleteMapping("/{crewId}")
	@Operation(operationId = "disbandCrew", summary = "크루 없애기 (2그룹)",
		description = "크루장이 크루를 없앤다. 크루장 말고 다른 크루원이 없을 때만 없앨 수 있다. "
			+ "없앤 크루는 목록과 크루 홈에서 사라지고, 남아 있던 초대 링크도 쓸 수 없다. "
			+ "크루 기록은 지우지 않고 상태만 「없앰」으로 바꾼다. 크루원·초대 기록은 지운다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "없애기 성공",
			content = @Content(examples = @ExampleObject(value = CrewApiExamples.DISBAND_SUCCESS))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "크루원이 아니거나 크루장이 아님",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "크루원이 아님", value = CrewApiExamples.CREW_NOT_MEMBER),
				@ExampleObject(name = "크루장이 아님", value = CrewApiExamples.CREW_LEADER_ONLY)
			})),
		@ApiResponse(responseCode = "404", description = "크루가 없거나 없앤 크루",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.CREW_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "크루장 말고 다른 크루원이 있음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.CREW_HAS_OTHER_MEMBERS)))
	})
	public ResponseEntity<RsData<Void>> disbandCrew(
		@LoginMemberId long memberId,
		@Parameter(description = "크루 ID", example = "1") @PathVariable long crewId
	) {
		crewFacade.disband(memberId, crewId);
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "크루를 없앴습니다.", null));
	}

	@PatchMapping("/{crewId}")
	@Operation(operationId = "updateCrew", summary = "크루 이름 바꾸기 (2그룹)",
		description = "크루장이 크루 이름을 바꾼다. 이름 규칙은 크루 만들기와 같다(앞뒤 공백을 뺀 1~20자, 다른 크루와 겹쳐도 된다). "
			+ "응답은 크루 홈과 같은 형식이라 바로 크루 홈을 다시 그린다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "바꾸기 성공",
			content = @Content(examples = @ExampleObject(value = CrewApiExamples.RENAME_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 요청 본문 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = CrewApiExamples.CREATE_INVALID_INPUT),
				@ExampleObject(name = "요청 본문 형식 오류", value = CrewApiExamples.INVALID_BODY)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "크루원이 아니거나 크루장이 아님",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "크루원이 아님", value = CrewApiExamples.CREW_NOT_MEMBER),
				@ExampleObject(name = "크루장이 아님", value = CrewApiExamples.CREW_LEADER_ONLY)
			})),
		@ApiResponse(responseCode = "404", description = "크루가 없거나 없앤 크루",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.CREW_NOT_FOUND)))
	})
	public ResponseEntity<RsData<CrewHomeDto>> updateCrew(
		@LoginMemberId long memberId,
		@Parameter(description = "크루 ID", example = "1") @PathVariable long crewId,
		@RequestBody CrewUpdateRequestDto request
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "크루 이름을 바꿨습니다.",
				crewFacade.rename(memberId, crewId, request.getName())));
	}
}
