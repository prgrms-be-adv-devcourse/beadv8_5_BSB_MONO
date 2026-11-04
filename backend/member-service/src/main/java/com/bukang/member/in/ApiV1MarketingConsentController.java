package com.bukang.member.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.member.app.MemberFacade;
import com.bukang.member.dto.MarketingUnsubscribeRequestDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 광고 메시지의 링크로 들어온 사람이 부르므로 로그인하지 않는다. 게이트웨이 공개 주소에 POST만 있다 (BUK-75, BE-039)
@Tag(name = "MarketingConsent", description = "광고 수신 거부 API (로그인 없이 수신 거부 링크로 부름)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/member/marketing-consent")
public class ApiV1MarketingConsentController {
	private final MemberFacade memberFacade;

	@PostMapping("/unsubscribe")
	@Operation(summary = "광고 수신 거부",
		description = "광고 메시지 끝의 링크(프론트 /unsubscribe?token=...) 확인 화면에서 버튼을 누르면 부른다. 로그인 없이 부를 수 있다. "
			+ "토큰의 서명과 기한(발급 뒤 1년)을 확인하고, 그 회원의 마케팅 수신 동의를 철회한다. "
			+ "이미 거부한 회원이 다시 눌러도 200이고 기록은 늘지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "수신 거부 성공 (이미 거부했어도 200)",
			content = @Content(examples = @ExampleObject(value = MemberApiExamples.UNSUBSCRIBE_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "토큰 누락 또는 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "토큰 누락", value = MemberApiExamples.UNSUBSCRIBE_TOKEN_MISSING),
				@ExampleObject(name = "형식 오류", value = MemberApiExamples.UNSUBSCRIBE_TOKEN_MALFORMED)
			})),
		@ApiResponse(responseCode = "401", description = "서명이 맞지 않는 토큰 (위조·변조)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = MemberApiExamples.UNSUBSCRIBE_TOKEN_INVALID))),
		@ApiResponse(responseCode = "404", description = "토큰의 회원이 없거나 탈퇴함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = MemberApiExamples.MEMBER_NOT_FOUND))),
		@ApiResponse(responseCode = "410", description = "기한이 지난 토큰",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = MemberApiExamples.UNSUBSCRIBE_TOKEN_EXPIRED)))
	})
	public ResponseEntity<RsData<Void>> unsubscribe(
		@Valid @RequestBody MarketingUnsubscribeRequestDto marketingUnsubscribeRequestDto
	) {
		memberFacade.unsubscribeMarketing(marketingUnsubscribeRequestDto.token());
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "광고 수신을 거부했습니다.", null));
	}
}
