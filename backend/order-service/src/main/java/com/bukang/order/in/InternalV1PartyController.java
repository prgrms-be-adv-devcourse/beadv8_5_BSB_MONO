package com.bukang.order.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.order.app.CrewMemberPartyQueryUseCase;
import com.bukang.order.dto.CrewMemberPartyStatusDto;
import com.bukang.order.dto.CrewPartyStatusDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 회원 서비스가 부르는 파티 내부 API. /internal/** 은 게이트웨이에 노출하지 않는다 (InternalV1OrderController와 같은 방식)
 */
@Tag(name = "PartyInternal", description = "크루 나가기·내보내기·없애기 확인용 파티 상태 조회 API (서비스 간 내부 호출 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/order")
public class InternalV1PartyController {
	private final CrewMemberPartyQueryUseCase crewMemberPartyQueryUseCase;

	@GetMapping("/crews/{crewId}/members/{memberId}/party-status")
	@Operation(summary = "크루원의 파티 상태 조회 (크루 나가기·내보내기 확인)",
		description = "회원 서비스가 크루원이 크루를 나가거나 크루장이 내보내기 전에 부른다 (11/05 회의 C1·C2). "
			+ "이 크루에서 회원이 아직 들어 있는 파티 중 모집 중·결제 진행 중·결제 끝인 것을 돌려준다. "
			+ "canLeave는 그런 파티가 하나도 없을 때 true, canKick은 결제 진행 중·결제 끝 파티가 없을 때 true다. "
			+ "결제 끝 파티는 대회 다음 날 종료되면 막지 않는다(11/09 결정). "
			+ "종료된 파티와 이미 나간 파티는 막지 않는다. 파티가 없으면 둘 다 true다. "
			+ "부르기가 실패하면 회원 서비스는 나가기·내보내기를 거절한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = {
				@ExampleObject(name = "막는 파티 없음", value = PartyInternalApiExamples.PARTY_STATUS_FREE),
				@ExampleObject(name = "모집 중 파티: 나가기만 막힘", value = PartyInternalApiExamples.PARTY_STATUS_RECRUITING),
				@ExampleObject(name = "결제 진행 중 파티: 둘 다 막힘", value = PartyInternalApiExamples.PARTY_STATUS_PAYING),
				@ExampleObject(name = "결제 끝 파티(대회 전): 둘 다 막힘", value = PartyInternalApiExamples.PARTY_STATUS_PAID)
			}))
	})
	public ResponseEntity<RsData<CrewMemberPartyStatusDto>> findPartyStatus(
		@Parameter(description = "크루 ID", example = "2") @PathVariable long crewId,
		@Parameter(description = "회원 ID", example = "3") @PathVariable long memberId
	) {
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "크루원의 파티 상태를 조회했습니다.",
			crewMemberPartyQueryUseCase.findPartyStatus(crewId, memberId)));
	}

	@GetMapping("/crews/{crewId}/party-status")
	@Operation(summary = "크루의 파티 상태 조회 (크루 없애기 확인)",
		description = "회원 서비스가 크루장이 크루를 없애기 전에 부른다 (11/09 결정). "
			+ "이 크루의 파티 중 모집 중·결제 진행 중·결제 끝인 것을 돌려준다. canDelete는 그런 파티가 하나도 없을 때 true다. "
			+ "결제 끝 파티는 대회 다음 날 종료된다. 부르기가 실패하면 회원 서비스는 없애기를 거절한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = {
				@ExampleObject(name = "막는 파티 없음", value = PartyInternalApiExamples.CREW_PARTY_STATUS_FREE),
				@ExampleObject(name = "진행 중인 파티 있음: 없애기 막힘",
					value = PartyInternalApiExamples.CREW_PARTY_STATUS_BLOCKED)
			}))
	})
	public ResponseEntity<RsData<CrewPartyStatusDto>> findCrewPartyStatus(
		@Parameter(description = "크루 ID", example = "2") @PathVariable long crewId
	) {
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "크루의 파티 상태를 조회했습니다.",
			crewMemberPartyQueryUseCase.findCrewPartyStatus(crewId)));
	}
}
