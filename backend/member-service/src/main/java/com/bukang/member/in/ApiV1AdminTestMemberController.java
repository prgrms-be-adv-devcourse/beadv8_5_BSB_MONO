package com.bukang.member.in;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.member.app.TestMemberUseCase;
import com.bukang.member.dto.TestMemberCreateRequestDto;
import com.bukang.member.dto.TestMemberDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * QA용 테스트 회원 만들기. member.auth.test-member.enabled가 true일 때만 빈을 만든다
 * 꺼져 있으면 이 주소가 아예 없어 404가 된다. 관리자가 아니면 403
 */
@Tag(name = "Admin", description = "관리자 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/member/admin/test-members")
@ConditionalOnProperty(name = "member.auth.test-member.enabled", havingValue = "true")
public class ApiV1AdminTestMemberController {
	private final TestMemberUseCase testMemberUseCase;

	@PostMapping
	@Operation(operationId = "createTestMembers", summary = "테스트 회원 만들기",
		description = "본인인증 없이 QA용 회원(qa-buyer-{번호}@crewrun.test)을 만든다. 이미 있는 번호는 건너뛰고 다음 번호로 만든다. "
			+ "비밀번호는 dev 시작 데이터와 같은 알려진 값이다. 실제 사용자가 있는 서버에서는 켜지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "만든 회원 목록"),
		@ApiResponse(responseCode = "400", description = "count가 1~20이 아님"),
		@ApiResponse(responseCode = "403", description = "관리자가 아님"),
		@ApiResponse(responseCode = "404", description = "테스트 회원 만들기가 꺼져 있음")
	})
	public ResponseEntity<RsData<List<TestMemberDto>>> create(
		@LoginMemberId long memberId,
		@Valid @RequestBody TestMemberCreateRequestDto request
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(RsData.of(HttpStatus.CREATED, "테스트 회원을 만들었습니다.",
				testMemberUseCase.create(memberId, request.count())));
	}
}
