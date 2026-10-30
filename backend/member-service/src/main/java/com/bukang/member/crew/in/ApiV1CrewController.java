package com.bukang.member.crew.in;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.member.crew.app.CrewFacade;
import com.bukang.member.crew.dto.CrewCreateRequestDto;
import com.bukang.member.crew.dto.CrewHomeDto;
import com.bukang.member.crew.dto.CrewInviteCreatedDto;
import com.bukang.member.crew.dto.CrewInviteDto;
import com.bukang.member.crew.dto.CrewInvitePreviewDto;
import com.bukang.member.crew.dto.CrewJoinedDto;
import com.bukang.member.crew.dto.MyCrewDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Crew", description = "크루 만들기, 내 크루 목록, 크루 홈, 초대 링크 만들기·조회·취소, 초대로 가입하기 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/member/crews")
public class ApiV1CrewController {
	private final CrewFacade crewFacade;

	@PostMapping
	@Operation(operationId = "createCrew", summary = "크루 만들기",
		description = "로그인한 회원이 크루 이름을 입력해 크루를 만든다. 만든 회원이 크루장이 되고, 가입 시각은 만든 시각과 같다. "
			+ "응답은 크루 홈과 같은 형식이라 바로 크루 홈을 그린다. "
			+ "한 회원이 크루 여러 개에 가입할 수 있는지는 회의에서 정한다(정해지면 409 오류 코드를 더한다).")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "만들기 성공",
			content = @Content(examples = @ExampleObject(value = CrewApiExamples.CREATE_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 요청 본문 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = CrewApiExamples.CREATE_INVALID_INPUT),
				@ExampleObject(name = "요청 본문 형식 오류", value = CrewApiExamples.INVALID_BODY)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.LOGIN_REQUIRED)))
	})
	public ResponseEntity<RsData<CrewHomeDto>> createCrew(
		@LoginMemberId long memberId,
		@RequestBody CrewCreateRequestDto request
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(RsData.of(HttpStatus.CREATED, "크루를 만들었습니다.", crewFacade.createCrew(memberId, request.getName())));
	}

	@GetMapping("/me")
	@Operation(operationId = "findMyCrews", summary = "내 크루 목록",
		description = "로그인한 회원이 가입한 크루를 가입한 순서로 돌려준다. 가입한 크루가 없으면 빈 배열이다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (가입한 크루가 없으면 빈 배열)",
			content = @Content(examples = @ExampleObject(value = CrewApiExamples.FIND_MY_CREWS_SUCCESS))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.LOGIN_REQUIRED)))
	})
	public ResponseEntity<RsData<List<MyCrewDto>>> findMyCrews(@LoginMemberId long memberId) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "내 크루 목록을 조회했습니다.", crewFacade.findMyCrews(memberId)));
	}

	@GetMapping("/{crewId}")
	@Operation(operationId = "findCrew", summary = "크루 홈",
		description = "크루 정보와 크루원 목록을 돌려준다. 크루원만 볼 수 있다. 크루원은 실명 없이 닉네임과 역할만 보이고, "
			+ "크루장, 운영진, 크루원 순서이며 같은 역할 안에서는 먼저 가입한 순서다. "
			+ "myRole로 초대 링크 만들기 버튼(크루장·운영진)과 크루 관리 버튼(크루장)을 보일지 정한다. "
			+ "진행 중인 파티는 파티 서비스 API에서 따로 가져온다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = CrewApiExamples.FIND_CREW_SUCCESS))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "크루원이 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.CREW_NOT_MEMBER))),
		@ApiResponse(responseCode = "404", description = "크루가 없거나 없앤 크루",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.CREW_NOT_FOUND)))
	})
	public ResponseEntity<RsData<CrewHomeDto>> findCrew(
		@LoginMemberId long memberId,
		@Parameter(description = "크루 ID", example = "1") @PathVariable long crewId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "크루를 조회했습니다.", crewFacade.findCrew(memberId, crewId)));
	}

	@PostMapping("/{crewId}/invites")
	@Operation(operationId = "createCrewInvite", summary = "초대 링크 만들기",
		description = "크루장과 운영진이 초대 링크를 만든다. 링크는 만든 때부터 24시간 동안 쓸 수 있다. "
			+ "응답의 inviteToken으로 프론트가 초대 받기 화면 주소를 만든다. 토큰은 이 응답에서만 받을 수 있다. "
			+ "초대 링크 하나로는 한 명만 가입한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "만들기 성공",
			content = @Content(examples = @ExampleObject(value = CrewApiExamples.CREATE_INVITE_SUCCESS))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "크루원이 아니거나 초대 권한이 없음 (크루원 역할)",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "크루원이 아님", value = CrewApiExamples.CREW_NOT_MEMBER),
				@ExampleObject(name = "초대 권한 없음", value = CrewApiExamples.CREW_INVITE_FORBIDDEN)
			})),
		@ApiResponse(responseCode = "404", description = "크루가 없거나 없앤 크루",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.CREW_NOT_FOUND)))
	})
	public ResponseEntity<RsData<CrewInviteCreatedDto>> createCrewInvite(
		@LoginMemberId long memberId,
		@Parameter(description = "크루 ID", example = "1") @PathVariable long crewId
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(RsData.of(HttpStatus.CREATED, "초대 링크를 만들었습니다.", crewFacade.createInvite(memberId, crewId)));
	}

	@GetMapping("/{crewId}/invites")
	@Operation(operationId = "findCrewInvites", summary = "보낸 초대 목록",
		description = "아직 쓸 수 있는 초대 링크(24시간 안, 취소·가입 전)를 돌려준다. 보낸 초대를 취소하는 화면에 쓴다. "
			+ "크루장은 크루의 모든 초대를, 운영진은 자기가 보낸 초대만 받는다. 토큰은 돌려주지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (없으면 빈 배열)",
			content = @Content(examples = @ExampleObject(value = CrewApiExamples.FIND_INVITES_SUCCESS))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "크루원이 아니거나 초대 권한이 없음 (크루원 역할)",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "크루원이 아님", value = CrewApiExamples.CREW_NOT_MEMBER),
				@ExampleObject(name = "초대 권한 없음", value = CrewApiExamples.CREW_INVITE_FORBIDDEN)
			})),
		@ApiResponse(responseCode = "404", description = "크루가 없거나 없앤 크루",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.CREW_NOT_FOUND)))
	})
	public ResponseEntity<RsData<List<CrewInviteDto>>> findCrewInvites(
		@LoginMemberId long memberId,
		@Parameter(description = "크루 ID", example = "1") @PathVariable long crewId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "초대 목록을 조회했습니다.", crewFacade.findInvites(memberId, crewId)));
	}

	@DeleteMapping("/{crewId}/invites/{inviteId}")
	@Operation(operationId = "cancelCrewInvite", summary = "초대 취소",
		description = "잘못 보낸 초대를 상대가 가입하기 전에 취소한다. 초대를 보낸 사람이나 크루장만 취소한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "취소 성공",
			content = @Content(examples = @ExampleObject(value = CrewApiExamples.CANCEL_INVITE_SUCCESS))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "크루원이 아니거나 취소 권한이 없음",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "크루원이 아님", value = CrewApiExamples.CREW_NOT_MEMBER),
				@ExampleObject(name = "취소 권한 없음", value = CrewApiExamples.CREW_INVITE_CANCEL_FORBIDDEN)
			})),
		@ApiResponse(responseCode = "404", description = "크루나 초대가 없음",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "크루 없음", value = CrewApiExamples.CREW_NOT_FOUND),
				@ExampleObject(name = "초대 없음", value = CrewApiExamples.CREW_INVITE_NOT_FOUND)
			})),
		@ApiResponse(responseCode = "409", description = "이미 가입에 쓰였거나 취소했거나 24시간이 지난 초대",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "이미 가입에 쓰임", value = CrewApiExamples.CANCEL_ALREADY_USED),
				@ExampleObject(name = "이미 취소함", value = CrewApiExamples.CANCEL_ALREADY_CANCELED),
				@ExampleObject(name = "24시간 지남", value = CrewApiExamples.CANCEL_EXPIRED)
			}))
	})
	public ResponseEntity<RsData<Void>> cancelCrewInvite(
		@LoginMemberId long memberId,
		@Parameter(description = "크루 ID", example = "1") @PathVariable long crewId,
		@Parameter(description = "초대 ID", example = "3") @PathVariable long inviteId
	) {
		crewFacade.cancelInvite(memberId, crewId, inviteId);
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "초대를 취소했습니다.", null));
	}

	@GetMapping("/invites/{inviteToken}")
	@Operation(operationId = "findCrewInvite", summary = "초대 링크 정보 조회",
		description = "초대 받기 화면에 크루 이름, 크루원 수, 링크가 끝나는 시각을 보여 준다. "
			+ "alreadyMember가 true면 이미 그 크루의 크루원이므로 프론트가 크루 홈으로 보낸다. 로그인이 필요하다. "
			+ "로그인하지 않았으면 프론트가 로그인·회원가입을 거친 뒤 같은 초대 화면으로 돌아온다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = CrewApiExamples.FIND_INVITE_SUCCESS))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "초대 토큰이 없음 (잘못된 링크) 또는 크루를 없앰",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.CREW_INVITE_NOT_FOUND))),
		@ApiResponse(responseCode = "410", description = "초대 링크를 더 쓸 수 없음 (24시간 지남, 취소됨, 이미 가입에 쓰임)",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "24시간 지남", value = CrewApiExamples.LINK_EXPIRED),
				@ExampleObject(name = "취소됨", value = CrewApiExamples.LINK_CANCELED),
				@ExampleObject(name = "이미 가입에 쓰임", value = CrewApiExamples.LINK_ALREADY_USED)
			}))
	})
	public ResponseEntity<RsData<CrewInvitePreviewDto>> findCrewInvite(
		@LoginMemberId long memberId,
		@Parameter(description = "초대 토큰", example = "q7Xk2LmZ9vBn4RtW8yCp1A") @PathVariable String inviteToken
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "초대 링크를 조회했습니다.", crewFacade.findInviteByToken(memberId, inviteToken)));
	}

	@PostMapping("/invites/{inviteToken}/join")
	@Operation(operationId = "joinCrew", summary = "초대 링크로 가입하기",
		description = "초대 링크로 크루에 가입한다. 역할은 크루원이고, 가입 시각은 서버가 이 요청을 처리한 시각이다. "
			+ "가입하면 가입 확정 알림을 보낸다. 같은 링크로 두 사람이 동시에 가입하면 한 사람만 성공한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "가입 성공",
			content = @Content(examples = @ExampleObject(value = CrewApiExamples.JOIN_SUCCESS))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "초대 토큰이 없음 (잘못된 링크) 또는 크루를 없앰",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.CREW_INVITE_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "이미 그 크루의 크루원 (프론트는 크루 홈으로 보낸다)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewApiExamples.CREW_ALREADY_MEMBER))),
		@ApiResponse(responseCode = "410", description = "초대 링크를 더 쓸 수 없음 (24시간 지남, 취소됨, 이미 가입에 쓰임)",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "24시간 지남", value = CrewApiExamples.LINK_EXPIRED),
				@ExampleObject(name = "취소됨", value = CrewApiExamples.LINK_CANCELED),
				@ExampleObject(name = "이미 가입에 쓰임", value = CrewApiExamples.LINK_ALREADY_USED)
			}))
	})
	public ResponseEntity<RsData<CrewJoinedDto>> joinCrew(
		@LoginMemberId long memberId,
		@Parameter(description = "초대 토큰", example = "q7Xk2LmZ9vBn4RtW8yCp1A") @PathVariable String inviteToken
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(RsData.of(HttpStatus.CREATED, "크루에 가입했습니다.", crewFacade.join(memberId, inviteToken)));
	}
}
