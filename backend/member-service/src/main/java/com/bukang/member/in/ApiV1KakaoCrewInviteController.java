package com.bukang.member.in;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.member.app.KakaoCrewInviteUseCase;
import com.bukang.member.app.KakaoMessageTokens;
import com.bukang.member.dto.KakaoFriendDto;
import com.bukang.member.dto.KakaoInviteSendRequestDto;
import com.bukang.member.dto.KakaoInviteSendResultDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 크루 초대 카카오톡 보내기 (kakao-crew-invite 결정 2·3). 게이트웨이가 Access Token을 검사한 요청만 온다
 * 카카오 추가 기능 심사 전에는 카카오 콘솔의 앱 멤버끼리만, 앱 전체 하루 30건까지 보낼 수 있다
 */
@Tag(name = "KakaoCrewInvite", description = "크루 초대 카카오톡 보내기 API")
@RestController
@RequiredArgsConstructor
public class ApiV1KakaoCrewInviteController {
	private final KakaoMessageTokens kakaoMessageTokens;
	private final KakaoCrewInviteUseCase kakaoCrewInviteUseCase;

	@PostMapping("/api/v1/member/members/me/kakao-message/consent-ticket")
	@Operation(operationId = "issueKakaoMessageConsentTicket", summary = "카카오 메시지 동의 표 발급",
		description = "친구 조회·보내기가 403 KAKAO_MESSAGE_CONSENT_REQUIRED면 부른다. 2분 동안 한 번만 쓸 수 있는 표를 "
			+ "httpOnly 쿠키(oauth2Link, Path=/api/v1/auth/oauth2/authorize)로 심는다. 프론트는 이어서 "
			+ "/api/v1/auth/oauth2/authorize/kakao?next=/crews/{crewId}/invites로 화면을 이동한다. 동의를 마치면 "
			+ "next?kakaoConsent=granted, 취소·범위 부족이면 next?error=KAKAO_CONSENT_DENIED, 연결된 카카오 계정과 다른 계정이면 "
			+ "next?error=KAKAO_ACCOUNT_MISMATCH로 돌아온다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "발급 성공",
			headers = @Header(name = HttpHeaders.SET_COOKIE,
				description = "oauth2Link=...; Path=/api/v1/auth/oauth2/authorize; Max-Age=120; "
					+ "Secure; HttpOnly; SameSite=Lax", schema = @Schema(type = "string")),
			content = @Content(examples = @ExampleObject(value = KakaoInviteApiExamples.CONSENT_TICKET_SUCCESS))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = KakaoInviteApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "409", description = "카카오 계정이 연결되지 않음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = KakaoInviteApiExamples.KAKAO_NOT_LINKED)))
	})
	public ResponseEntity<RsData<Void>> issueConsentTicket(@LoginMemberId long memberId) {
		String ticket = kakaoMessageTokens.issueConsentTicket(memberId);
		return ResponseEntity.status(HttpStatus.CREATED)
			.header(HttpHeaders.SET_COOKIE, AuthCookies.oauth2Link(ticket).toString())
			.body(RsData.of(HttpStatus.CREATED, "동의 표 발급 성공", null));
	}

	@GetMapping("/api/v1/member/crews/{crewId}/kakao-invites/friends")
	@Operation(operationId = "findKakaoInviteFriends", summary = "카카오톡으로 초대할 수 있는 친구 조회",
		description = "크루장과 운영진만 부른다. 카카오 친구 중 크루런 회원이고 아직 이 크루원이 아닌 사람을 카카오톡 프로필로 돌려준다. "
			+ "친구도 카카오 메시지 동의를 마쳐야 목록에 나온다. 친구 정보는 저장하지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (친구가 없으면 빈 배열)",
			content = @Content(examples = @ExampleObject(value = KakaoInviteApiExamples.FRIENDS_SUCCESS))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = KakaoInviteApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "크루원이 아님, 초대 권한 없음, 카카오 메시지 동의 필요",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "크루원이 아님", value = KakaoInviteApiExamples.CREW_NOT_MEMBER),
				@ExampleObject(name = "초대 권한 없음", value = KakaoInviteApiExamples.CREW_INVITE_FORBIDDEN),
				@ExampleObject(name = "동의 필요", value = KakaoInviteApiExamples.CONSENT_REQUIRED)
			})),
		@ApiResponse(responseCode = "404", description = "크루가 없거나 없앤 크루",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = KakaoInviteApiExamples.CREW_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "카카오 계정이 연결되지 않음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = KakaoInviteApiExamples.KAKAO_NOT_LINKED))),
		@ApiResponse(responseCode = "502", description = "카카오가 응답하지 않음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = KakaoInviteApiExamples.KAKAO_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<List<KakaoFriendDto>>> findFriends(
		@LoginMemberId long memberId,
		@Parameter(description = "크루 ID", example = "1") @PathVariable long crewId
	) {
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "카카오 친구를 조회했습니다.",
			kakaoCrewInviteUseCase.findFriends(memberId, crewId)));
	}

	@PostMapping("/api/v1/member/crews/{crewId}/kakao-invites")
	@Operation(operationId = "sendKakaoCrewInvites", summary = "크루 초대 카카오톡 보내기",
		description = "크루장과 운영진만 부른다. 받는 사람마다 새 초대 링크(24시간)를 만들어 카카오톡 메시지 버튼에 담아 "
			+ "한 명씩 보낸다. 보내지 못한 몫의 링크는 취소한다. 한 명이라도 보냈으면 200이고 실패는 failures로 알린다. "
			+ "초대 토큰은 응답에 싣지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "한 명 이상 보냄",
			content = @Content(examples = @ExampleObject(value = KakaoInviteApiExamples.SEND_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "받는 사람이 없거나 6명 이상이거나 중복",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = KakaoInviteApiExamples.INVALID_RECEIVERS))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = KakaoInviteApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "크루원이 아님, 초대 권한 없음, 카카오 메시지 동의 필요",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "크루원이 아님", value = KakaoInviteApiExamples.CREW_NOT_MEMBER),
				@ExampleObject(name = "초대 권한 없음", value = KakaoInviteApiExamples.CREW_INVITE_FORBIDDEN),
				@ExampleObject(name = "동의 필요", value = KakaoInviteApiExamples.CONSENT_REQUIRED)
			})),
		@ApiResponse(responseCode = "404", description = "크루가 없거나 없앤 크루",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = KakaoInviteApiExamples.CREW_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "카카오 계정이 연결되지 않음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = KakaoInviteApiExamples.KAKAO_NOT_LINKED))),
		@ApiResponse(responseCode = "429", description = "모든 받는 사람이 카카오 하루 한도에 걸림",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = KakaoInviteApiExamples.DAILY_LIMIT))),
		@ApiResponse(responseCode = "502", description = "카카오가 응답하지 않아 한 명도 보내지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = KakaoInviteApiExamples.KAKAO_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<KakaoInviteSendResultDto>> send(
		@LoginMemberId long memberId,
		@Parameter(description = "크루 ID", example = "1") @PathVariable long crewId,
		@Valid @RequestBody KakaoInviteSendRequestDto request
	) {
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "카카오톡으로 초대를 보냈습니다.",
			kakaoCrewInviteUseCase.send(memberId, crewId, request.receiverUuids())));
	}
}
