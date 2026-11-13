package com.bukang.board.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.board.app.BoardAdminReportUseCase;
import com.bukang.board.app.BoardReportUseCase;
import com.bukang.board.domain.ReportHandledAction;
import com.bukang.board.domain.ReportTargetType;
import com.bukang.board.dto.AdminReportDto;
import com.bukang.board.dto.ReportRequestDto;
import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 글·댓글 신고와 관리자 신고 관리. 관리자 주소의 GET도 게이트웨이는 토큰 없이 통과시키므로, 관리자인지는 게시판이 직접 확인한다
 */
@Tag(name = "BoardReport", description = "게시판 글·댓글 신고, 관리자 신고 목록·되살리기·지우기 API")
@RestController
@RequestMapping("/api/v1/board")
@RequiredArgsConstructor
public class ApiV1BoardReportController {
	private final BoardReportUseCase boardReportUseCase;
	private final BoardAdminReportUseCase boardAdminReportUseCase;

	@PostMapping("/posts/{postId}/report")
	@Operation(summary = "글 신고", description = "글을 신고한다. 서로 다른 회원 5명이 신고하면 글이 숨겨진다. 같은 회원이 다시 신고해도 1건으로 세고 같은 응답을 준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "신고함 (이미 신고한 글이어도 201)"),
		@ApiResponse(responseCode = "400", description = "내 글을 신고함(BOARD_SELF_REPORT) 또는 사유가 없거나 올바르지 않음"),
		@ApiResponse(responseCode = "401", description = "로그인하지 않음"),
		@ApiResponse(responseCode = "403", description = "탈퇴한 회원(BOARD_MEMBER_WITHDRAWN)"),
		@ApiResponse(responseCode = "404", description = "없는 글, 지운 글, 숨겨진 글(BOARD_POST_NOT_FOUND)")
	})
	public ResponseEntity<RsData<Void>> reportPost(@LoginMemberId long memberId,
		@Parameter(description = "글 ID", example = "7") @PathVariable long postId,
		@Valid @RequestBody ReportRequestDto request) {
		boardReportUseCase.reportPost(memberId, postId, request.reason());
		return ResponseEntity.status(HttpStatus.CREATED).body(RsData.of(HttpStatus.CREATED, "신고했습니다.", null));
	}

	@PostMapping("/comments/{commentId}/report")
	@Operation(summary = "댓글 신고", description = "댓글(또는 답글)을 신고한다. 서로 다른 회원 5명이 신고하면 숨겨진다. 같은 회원의 중복 신고는 1건이다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "신고함 (이미 신고한 댓글이어도 201)"),
		@ApiResponse(responseCode = "400", description = "내 댓글을 신고함(BOARD_SELF_REPORT) 또는 사유가 없거나 올바르지 않음"),
		@ApiResponse(responseCode = "401", description = "로그인하지 않음"),
		@ApiResponse(responseCode = "403", description = "탈퇴한 회원(BOARD_MEMBER_WITHDRAWN)"),
		@ApiResponse(responseCode = "404", description = "없는·지운·숨겨진 댓글이거나 숨겨진 글의 댓글(BOARD_COMMENT_NOT_FOUND)")
	})
	public ResponseEntity<RsData<Void>> reportComment(@LoginMemberId long memberId,
		@Parameter(description = "댓글 ID", example = "21") @PathVariable long commentId,
		@Valid @RequestBody ReportRequestDto request) {
		boardReportUseCase.reportComment(memberId, commentId, request.reason());
		return ResponseEntity.status(HttpStatus.CREATED).body(RsData.of(HttpStatus.CREATED, "신고했습니다.", null));
	}

	@GetMapping("/admin/reports")
	@Operation(summary = "관리자 신고 목록", description = "숨겨진 글·댓글과 관리자가 처리한 글·댓글을 준다. 관리자만 볼 수 있다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공"),
		@ApiResponse(responseCode = "401", description = "로그인하지 않음"),
		@ApiResponse(responseCode = "403", description = "관리자가 아님(BOARD_ADMIN_FORBIDDEN)"),
		@ApiResponse(responseCode = "503", description = "회원 서비스에 연결하지 못해 관리자인지 모름")
	})
	public RsData<AdminReportDto> reports(@LoginMemberId long memberId) {
		return RsData.of(HttpStatus.OK, "신고 목록을 조회했습니다.", boardAdminReportUseCase.list(memberId));
	}

	@PostMapping("/admin/reports/{targets}/{targetId}/{action}")
	@Operation(summary = "숨겨진 글·댓글 처리",
		description = "숨겨진 글·댓글을 되살리거나(restore) 지운다(delete). targets는 posts 또는 comments다. 관리자만 할 수 있다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리함"),
		@ApiResponse(responseCode = "401", description = "로그인하지 않음"),
		@ApiResponse(responseCode = "403", description = "관리자가 아님(BOARD_ADMIN_FORBIDDEN)"),
		@ApiResponse(responseCode = "404", description = "없는 글·댓글이거나 알 수 없는 targets·action(BOARD_REPORT_NOT_FOUND)"),
		@ApiResponse(responseCode = "409", description = "숨겨지지 않았거나 이미 처리함(BOARD_REPORT_NOT_HANDLEABLE)"),
		@ApiResponse(responseCode = "503", description = "회원 서비스에 연결하지 못해 관리자인지 모름")
	})
	public RsData<Void> handle(@LoginMemberId long memberId,
		@Parameter(description = "posts 또는 comments", example = "posts") @PathVariable String targets,
		@Parameter(description = "글 ID 또는 댓글 ID", example = "7") @PathVariable long targetId,
		@Parameter(description = "restore 또는 delete", example = "restore") @PathVariable String action) {
		boardAdminReportUseCase.handle(memberId, ReportTargetType.fromPath(targets), targetId,
			ReportHandledAction.fromPath(action));
		return RsData.of(HttpStatus.OK, "처리했습니다.", null);
	}
}
