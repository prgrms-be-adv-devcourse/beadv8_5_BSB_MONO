package com.bukang.member.crew.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.member.crew.app.CrewFacade;
import com.bukang.member.crew.dto.CrewMemberCountDto;
import com.bukang.member.crew.dto.CrewMemberStatusDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 파티 서비스가 동기 호출로 부르는 크루 내부 API. /internal/** 은 게이트웨이에 노출하지 않는다
 * 회원이 아니라 서비스가 부르므로 회원 ID 헤더를 받지 않는다 (InternalV1MemberController와 같은 방식)
 */
@Tag(name = "CrewInternal", description = "크루원 여부·가입 시각 조회, 크루원 수 조회 API (서비스 간 내부 호출 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/member/crews")
public class InternalV1CrewController {
	private final CrewFacade crewFacade;

	@GetMapping("/{crewId}/members/{memberId}")
	@Operation(summary = "크루원 여부와 가입 시각 조회",
		description = "파티 서비스가 파티 만들기·합류·신청 때 부른다. 크루원이 아니어도 200이며 crewMember로 판단한다. "
			+ "크루원이 아니면 role·joinedAt은 null이다. 크루가 없거나 없앤 크루면 404이다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (크루원이 아니어도 200)",
			content = @Content(examples = {
				@ExampleObject(name = "크루원", value = CrewInternalApiExamples.CREW_MEMBER),
				@ExampleObject(name = "크루원이 아님", value = CrewInternalApiExamples.NOT_CREW_MEMBER)
			})),
		@ApiResponse(responseCode = "404", description = "크루가 없거나 없앤 크루",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewInternalApiExamples.CREW_NOT_FOUND)))
	})
	public ResponseEntity<RsData<CrewMemberStatusDto>> findMemberStatus(
		@Parameter(description = "크루 ID", example = "1") @PathVariable long crewId,
		@Parameter(description = "확인할 회원 ID", example = "2") @PathVariable long memberId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "크루원 여부를 조회했습니다.", crewFacade.findMemberStatus(crewId, memberId)));
	}

	@GetMapping("/{crewId}/member-count")
	@Operation(summary = "크루원 수 조회",
		description = "파티 서비스가 크루원 4명 이상 신청 조건을 확인할 때 부른다. 크루장, 운영진, 크루원을 모두 센다. "
			+ "크루가 없거나 없앤 크루면 404이다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = CrewInternalApiExamples.MEMBER_COUNT))),
		@ApiResponse(responseCode = "404", description = "크루가 없거나 없앤 크루",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = CrewInternalApiExamples.CREW_NOT_FOUND)))
	})
	public ResponseEntity<RsData<CrewMemberCountDto>> findMemberCount(
		@Parameter(description = "크루 ID", example = "1") @PathVariable long crewId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "크루원 수를 조회했습니다.", crewFacade.findMemberCount(crewId)));
	}
}
