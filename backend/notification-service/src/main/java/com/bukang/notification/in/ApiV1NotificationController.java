package com.bukang.notification.in;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.notification.app.NotificationUseCase;
import com.bukang.notification.dto.NoticeDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Notification", description = "내 알림 조회, 알림 읽음, 모두 읽음 API")
@RestController
@RequestMapping("/api/v1/notification/notifications")
@RequiredArgsConstructor
public class ApiV1NotificationController {
	private final NotificationUseCase notificationUseCase;

	@GetMapping
	@Operation(summary = "내 알림",
		description = "최근 7일 내 알림을 최신순으로 준다. 시각(at)·묶음(group)은 한국 시간 기준으로 서버가 정한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = NotificationApiExamples.FIND_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "회원 ID가 0 이하이거나 숫자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = NotificationApiExamples.INVALID_MEMBER_ID))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = NotificationApiExamples.LOGIN_REQUIRED)))
	})
	public RsData<List<NoticeDto>> findMine(@LoginMemberId long memberId) {
		return RsData.of(HttpStatus.OK, "알림을 조회했습니다.", notificationUseCase.findMine(memberId));
	}

	@PostMapping("/{notificationId}/read")
	@Operation(summary = "알림 읽음", description = "내 알림 하나를 읽음으로 바꾼다. 이미 읽었으면 그대로 둔다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "읽음 처리 성공",
			content = @Content(examples = @ExampleObject(value = NotificationApiExamples.READ_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "알림 ID 형식 오류, 회원 ID가 0 이하이거나 숫자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "알림 ID 형식 오류", value = NotificationApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID 오류", value = NotificationApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = NotificationApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "없는 알림이거나 다른 회원의 알림",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = NotificationApiExamples.NOT_FOUND)))
	})
	public RsData<Void> read(@LoginMemberId long memberId,
		@Parameter(description = "알림 ID", example = "12") @PathVariable long notificationId) {
		notificationUseCase.read(memberId, notificationId);
		return RsData.of(HttpStatus.OK, "알림을 읽음으로 바꿨습니다.", null);
	}

	@PostMapping("/read-all")
	@Operation(summary = "모두 읽음", description = "안 읽은 내 알림을 모두 읽음으로 바꾼다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "모두 읽음 처리 성공",
			content = @Content(examples = @ExampleObject(value = NotificationApiExamples.READ_ALL_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "회원 ID가 0 이하이거나 숫자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = NotificationApiExamples.INVALID_MEMBER_ID))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = NotificationApiExamples.LOGIN_REQUIRED)))
	})
	public RsData<Void> readAll(@LoginMemberId long memberId) {
		notificationUseCase.readAll(memberId);
		return RsData.of(HttpStatus.OK, "알림을 모두 읽음으로 바꿨습니다.", null);
	}
}
