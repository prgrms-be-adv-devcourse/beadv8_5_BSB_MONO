package com.bukang.order.in;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.order.app.PartyFacade;
import com.bukang.order.dto.ParticipationDto;
import com.bukang.order.dto.PartyCreateRequestDto;
import com.bukang.order.dto.PartyCreatedDto;
import com.bukang.order.dto.PartyDto;
import com.bukang.order.dto.PartyJoinRequestDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Party", description = "파티 만들기, 참가 링크 다시 만들기, 합류, 나가기 API (BUK-36)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/order/parties")
public class ApiV1PartyController {
	private final PartyFacade partyFacade;

	@PostMapping
	@Operation(summary = "파티 만들기",
		description = "크루원이 파티를 만든다. 파티장도 참가자라 자기 종목·참가자 정보로 파티원 신청서(결제 전)를 함께 만든다. "
			+ "파티는 고른 종목의 대회 단위이고, 참가 링크(24시간)가 함께 만들어진다. "
			+ "같은 대회에 개인 신청이 남아 있으면 409 ORDER_INDIVIDUAL_PARTICIPATION_EXISTS와 data.participationId를 준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "만들기 성공"),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패, 나이 제한 밖, 키트 종목의 배송 주소 없음, 티셔츠 사이즈 오류"),
		@ApiResponse(responseCode = "403", description = "이 크루의 크루원이 아님 (ORDER_CREW_MEMBER_REQUIRED)"),
		@ApiResponse(responseCode = "404", description = "없는 종목 또는 없는 크루"),
		@ApiResponse(responseCode = "409", description = "같은 대회에 개인 신청·다른 파티 신청"
			+ "(ORDER_PARTY_OTHER_PARTY_JOINED, data.partyId)이 있음, 취소된 대회, 접수 마감"),
		@ApiResponse(responseCode = "503", description = "회원·상품 서비스에 연결하지 못함")
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
	@Operation(summary = "참가 링크 다시 만들기",
		description = "파티장만, 모집 중일 때 언제든 다시 만든다. 새로 만들면 옛 링크로는 합류할 수 없다(404).")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "다시 만들기 성공"),
		@ApiResponse(responseCode = "403", description = "파티장이 아님 (ORDER_PARTY_LEADER_ONLY)"),
		@ApiResponse(responseCode = "404", description = "없는 파티"),
		@ApiResponse(responseCode = "409",
			description = "모집 중이 아님 (ORDER_PARTY_NOT_RECRUITING, data.partyId·data.partyStatus)")
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
	@Operation(summary = "참가 링크로 합류",
		description = "그 크루의 크루원이 참가 링크로 들어와 종목·참가자 정보를 넣고 합류를 확정한다. 파티원 신청서(결제 전)가 만들어진다. "
			+ "나갔던 사람도 다시 합류할 수 있다. 같은 대회에 개인 신청이 남아 있으면 409 "
			+ "ORDER_INDIVIDUAL_PARTICIPATION_EXISTS와 data.participationId를 준다(개인 신청을 먼저 취소).")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "합류 성공"),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패, 나이 제한 밖, 키트 종목의 배송 주소 없음, 티셔츠 사이즈 오류"),
		@ApiResponse(responseCode = "403", description = "이 크루의 크루원이 아님 (ORDER_CREW_MEMBER_REQUIRED)"),
		@ApiResponse(responseCode = "404", description = "참가 링크가 없음(다시 만든 옛 링크 포함), 없는 종목 또는 크루"),
		@ApiResponse(responseCode = "409", description = "링크 만료(ORDER_PARTY_LINK_EXPIRED), "
			+ "모집 중이 아님(ORDER_PARTY_NOT_RECRUITING, data.partyId·data.partyStatus), "
			+ "다른 대회의 종목(ORDER_PARTY_RACE_MISMATCH), 이미 합류함(ORDER_PARTY_ALREADY_JOINED, data.partyId), "
			+ "다른 파티로 신청함(ORDER_PARTY_OTHER_PARTY_JOINED, data.partyId = 그 파티), "
			+ "개인 신청이 있음(ORDER_INDIVIDUAL_PARTICIPATION_EXISTS, data.participationId), 취소된 대회, 접수 마감"),
		@ApiResponse(responseCode = "503", description = "회원·상품 서비스에 연결하지 못함")
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
	@Operation(summary = "파티 나가기",
		description = "신청 전(모집 중)에만 나갈 수 있다. 내 파티원 신청서는 취소되어 그 대회에 다시 신청할 수 있다. "
			+ "파티장이 나가면 가장 먼저 합류한 파티원이 파티장이 되고, 남은 사람이 없으면 파티가 종료된다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "나가기 성공 (취소된 내 신청서)"),
		@ApiResponse(responseCode = "403", description = "이 파티의 파티원이 아님 (ORDER_PARTY_NOT_MEMBER)"),
		@ApiResponse(responseCode = "404", description = "없는 파티"),
		@ApiResponse(responseCode = "409",
			description = "모집 중이 아님 (ORDER_PARTY_NOT_RECRUITING, data.partyId·data.partyStatus)")
	})
	public ResponseEntity<RsData<ParticipationDto>> leave(
		@LoginMemberId long memberId,
		@Parameter(description = "파티 ID", example = "1") @PathVariable long partyId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "파티에서 나갔습니다.", partyFacade.leave(partyId, memberId)));
	}
}
