package com.bukang.board.in;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.board.app.BoardCommentUseCase;
import com.bukang.board.app.BoardLikeUseCase;
import com.bukang.board.app.BoardPostUseCase;
import com.bukang.board.app.BoardQueryUseCase;
import com.bukang.board.dto.CommentDto;
import com.bukang.board.dto.CommentIdDto;
import com.bukang.board.dto.CommentSaveRequestDto;
import com.bukang.board.dto.LikeDto;
import com.bukang.board.dto.PostDetailDto;
import com.bukang.board.dto.PostIdDto;
import com.bukang.board.dto.PostSaveRequestDto;
import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 커뮤니티 자유 게시판. 읽기(GET)는 로그인 없이 되고(게이트웨이 공개 경로), 쓰기는 로그인한 회원만 한다.
 * 공개 주소라도 토큰이 있으면 게이트웨이가 회원 ID를 넘기므로 「내가 누른 좋아요」, 「내 숨겨진 글」을 같은 주소로 보여 준다
 */
@Tag(name = "Board", description = "게시판 글 쓰기·조회·수정·삭제, 댓글·답글, 좋아요 API")
@RestController
@RequestMapping("/api/v1/board")
@RequiredArgsConstructor
public class ApiV1BoardController {
	private final BoardPostUseCase boardPostUseCase;
	private final BoardCommentUseCase boardCommentUseCase;
	private final BoardLikeUseCase boardLikeUseCase;
	private final BoardQueryUseCase boardQueryUseCase;

	@PostMapping("/posts")
	@Operation(summary = "글 쓰기",
		description = "글을 만든다. 말머리를 비우면 자유다. 사진 파일 ID가 있으면 저장이 끝난 뒤 file-service에 연결을 요청한다. 글은 1분에 1개까지 쓸 수 있다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "등록 성공",
			content = @Content(examples = @ExampleObject(value = BoardApiExamples.POST_CREATED))),
		@ApiResponse(responseCode = "400", description = "입력 오류 (제목·본문·댓글 글자 수, 사진 6장 이상, 모르는 말머리), 회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력 값 오류", value = BoardApiExamples.INVALID_BODY),
				@ExampleObject(name = "회원 ID 오류", value = BoardApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인 안 함)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = BoardApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "429", description = "1분 안에 또 글을 씀",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = BoardApiExamples.TOO_FAST)))
	})
	public ResponseEntity<RsData<PostIdDto>> createPost(@LoginMemberId long memberId,
		@Valid @RequestBody PostSaveRequestDto request) {
		long postId = boardPostUseCase.create(memberId, request);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(RsData.of(HttpStatus.CREATED, "글을 등록했습니다.", new PostIdDto(postId)));
	}

	@GetMapping("/posts/{postId}")
	@Operation(summary = "글 상세",
		description = "글 하나를 준다. 로그인 없이 볼 수 있고, 로그인했으면 내가 쓴 글인지와 좋아요를 눌렀는지도 담는다. 신고로 숨겨진 글은 쓴 사람에게만 보인다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = BoardApiExamples.POST_DETAIL))),
		@ApiResponse(responseCode = "400", description = "경로 값 형식 오류, 회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "경로 값 형식 오류", value = BoardApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID 오류", value = BoardApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "404", description = "없는 글, 지운 글, 남의 숨겨진 글",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = BoardApiExamples.POST_NOT_FOUND)))
	})
	public RsData<PostDetailDto> getPost(@LoginMemberId(required = false) @Nullable Long memberId,
		@Parameter(description = "글 ID", example = "7") @PathVariable long postId) {
		return RsData.of(HttpStatus.OK, "글을 조회했습니다.", boardQueryUseCase.getPost(memberId, postId));
	}

	@PutMapping("/posts/{postId}")
	@Operation(summary = "글 고치기",
		description = "내 글의 말머리·제목·본문을 고치고 수정됨으로 표시한다. 사진 파일 ID를 빼면 사진은 그대로 두고, 빈 목록이면 사진을 모두 뗀다. 숨겨진 글은 고칠 수 없다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "수정 성공",
			content = @Content(examples = @ExampleObject(value = BoardApiExamples.POST_UPDATED))),
		@ApiResponse(responseCode = "400", description = "입력 오류 (제목·본문·댓글 글자 수, 사진 6장 이상, 모르는 말머리), 회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력 값 오류", value = BoardApiExamples.INVALID_BODY),
				@ExampleObject(name = "회원 ID 오류", value = BoardApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인 안 함)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = BoardApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "남의 글",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = BoardApiExamples.NOT_WRITER))),
		@ApiResponse(responseCode = "404", description = "없는 글, 지운 글, 남의 숨겨진 글",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = BoardApiExamples.POST_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "신고로 숨겨진 내 글",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = BoardApiExamples.POST_HIDDEN)))
	})
	public RsData<Void> updatePost(@LoginMemberId long memberId,
		@Parameter(description = "글 ID", example = "7") @PathVariable long postId,
		@Valid @RequestBody PostSaveRequestDto request) {
		boardPostUseCase.update(memberId, postId, request);
		return RsData.of(HttpStatus.OK, "글을 수정했습니다.", null);
	}

	@DeleteMapping("/posts/{postId}")
	@Operation(summary = "글 지우기", description = "내 글을 지움 상태로 바꾼다 (행은 남는다). 숨겨진 내 글도 지울 수 있다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "삭제 성공",
			content = @Content(examples = @ExampleObject(value = BoardApiExamples.POST_DELETED))),
		@ApiResponse(responseCode = "400", description = "경로 값 형식 오류, 회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "경로 값 형식 오류", value = BoardApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID 오류", value = BoardApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인 안 함)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = BoardApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "남의 글",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = BoardApiExamples.NOT_WRITER))),
		@ApiResponse(responseCode = "404", description = "없는 글, 지운 글, 남의 숨겨진 글",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = BoardApiExamples.POST_NOT_FOUND)))
	})
	public RsData<Void> deletePost(@LoginMemberId long memberId,
		@Parameter(description = "글 ID", example = "7") @PathVariable long postId) {
		boardPostUseCase.delete(memberId, postId);
		return RsData.of(HttpStatus.OK, "글을 삭제했습니다.", null);
	}

	@GetMapping("/posts/{postId}/comments")
	@Operation(summary = "댓글 목록",
		description = "글의 댓글을 오래된 순으로 주고, 답글은 부모 댓글의 replies에 묶는다. 답글이 달린 댓글을 지웠으면 내용 없이 DELETED 상태로 남긴다. 로그인 없이 볼 수 "
				+ "있다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = BoardApiExamples.COMMENT_LIST))),
		@ApiResponse(responseCode = "400", description = "경로 값 형식 오류, 회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "경로 값 형식 오류", value = BoardApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID 오류", value = BoardApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "404", description = "없는 글, 지운 글, 남의 숨겨진 글",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = BoardApiExamples.POST_NOT_FOUND)))
	})
	public RsData<List<CommentDto>> getComments(
		@LoginMemberId(required = false) @Nullable Long memberId,
		@Parameter(description = "글 ID", example = "7") @PathVariable long postId) {
		return RsData.of(HttpStatus.OK, "댓글을 조회했습니다.", boardQueryUseCase.getComments(memberId, postId));
	}

	@PostMapping("/posts/{postId}/comments")
	@Operation(summary = "댓글·답글 쓰기",
		description = "글에 댓글을 단다. parentId를 넣으면 그 댓글의 답글이다 (한 단계만). 댓글은 10초에 1개까지 쓸 수 있다. 숨겨진 글에는 쓸 수 없다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "등록 성공",
			content = @Content(examples = @ExampleObject(value = BoardApiExamples.COMMENT_CREATED))),
		@ApiResponse(responseCode = "400", description = "입력 오류, 답글의 답글, 회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력 값 오류", value = BoardApiExamples.INVALID_BODY),
				@ExampleObject(name = "답글의 답글", value = BoardApiExamples.REPLY_DEPTH),
				@ExampleObject(name = "회원 ID 오류", value = BoardApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인 안 함)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = BoardApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "없는 글·지운 글·남의 숨겨진 글, 또는 없는·지운·다른 글의 부모 댓글",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "글 없음", value = BoardApiExamples.POST_NOT_FOUND),
				@ExampleObject(name = "부모 댓글 없음", value = BoardApiExamples.COMMENT_NOT_FOUND)
			})),
		@ApiResponse(responseCode = "409", description = "신고로 숨겨진 내 글",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = BoardApiExamples.POST_HIDDEN))),
		@ApiResponse(responseCode = "429", description = "10초 안에 또 댓글을 씀",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = BoardApiExamples.TOO_FAST)))
	})
	public ResponseEntity<RsData<CommentIdDto>> createComment(@LoginMemberId long memberId,
		@Parameter(description = "글 ID", example = "7") @PathVariable long postId,
		@Valid @RequestBody CommentSaveRequestDto request) {
		long commentId = boardCommentUseCase.create(memberId, postId, request);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(RsData.of(HttpStatus.CREATED, "댓글을 등록했습니다.", new CommentIdDto(commentId)));
	}

	@PutMapping("/comments/{commentId}")
	@Operation(summary = "댓글 고치기", description = "내 댓글(또는 답글)의 내용을 고치고 수정됨으로 표시한다. parentId는 쓰지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "수정 성공",
			content = @Content(examples = @ExampleObject(value = BoardApiExamples.COMMENT_UPDATED))),
		@ApiResponse(responseCode = "400", description = "입력 오류 (제목·본문·댓글 글자 수, 사진 6장 이상, 모르는 말머리), 회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력 값 오류", value = BoardApiExamples.INVALID_BODY),
				@ExampleObject(name = "회원 ID 오류", value = BoardApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인 안 함)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = BoardApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "남의 댓글",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = BoardApiExamples.NOT_WRITER))),
		@ApiResponse(responseCode = "404", description = "없는·지운 댓글, 지운 글의 댓글",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = BoardApiExamples.COMMENT_NOT_FOUND)))
	})
	public RsData<Void> updateComment(@LoginMemberId long memberId,
		@Parameter(description = "댓글 ID", example = "21") @PathVariable long commentId,
		@Valid @RequestBody CommentSaveRequestDto request) {
		boardCommentUseCase.update(memberId, commentId, request);
		return RsData.of(HttpStatus.OK, "댓글을 수정했습니다.", null);
	}

	@DeleteMapping("/comments/{commentId}")
	@Operation(summary = "댓글 지우기", description = "내 댓글(또는 답글)을 지움 상태로 바꾼다 (행은 남는다). 답글이 달린 댓글은 목록에 내용 없이 DELETED로 남는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "삭제 성공",
			content = @Content(examples = @ExampleObject(value = BoardApiExamples.COMMENT_DELETED))),
		@ApiResponse(responseCode = "400", description = "경로 값 형식 오류, 회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "경로 값 형식 오류", value = BoardApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID 오류", value = BoardApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인 안 함)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = BoardApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "남의 댓글",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = BoardApiExamples.NOT_WRITER))),
		@ApiResponse(responseCode = "404", description = "없는·지운 댓글, 지운 글의 댓글",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = BoardApiExamples.COMMENT_NOT_FOUND)))
	})
	public RsData<Void> deleteComment(@LoginMemberId long memberId,
		@Parameter(description = "댓글 ID", example = "21") @PathVariable long commentId) {
		boardCommentUseCase.delete(memberId, commentId);
		return RsData.of(HttpStatus.OK, "댓글을 삭제했습니다.", null);
	}

	@PutMapping("/posts/{postId}/like")
	@Operation(summary = "좋아요 누르기", description = "글에 좋아요를 누른다. 회원당 글 하나에 한 번이라 이미 눌렀으면 그대로 두고 현재 상태를 준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "성공",
			content = @Content(examples = @ExampleObject(value = BoardApiExamples.LIKED))),
		@ApiResponse(responseCode = "400", description = "경로 값 형식 오류, 회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "경로 값 형식 오류", value = BoardApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID 오류", value = BoardApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인 안 함)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = BoardApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "없는 글, 지운 글, 남의 숨겨진 글",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = BoardApiExamples.POST_NOT_FOUND)))
	})
	public RsData<LikeDto> like(@LoginMemberId long memberId,
		@Parameter(description = "글 ID", example = "7") @PathVariable long postId) {
		return RsData.of(HttpStatus.OK, "좋아요를 눌렀습니다.", boardLikeUseCase.like(memberId, postId));
	}

	@DeleteMapping("/posts/{postId}/like")
	@Operation(summary = "좋아요 취소", description = "누른 좋아요를 취소한다. 누른 적이 없으면 그대로 두고 현재 상태를 준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "성공",
			content = @Content(examples = @ExampleObject(value = BoardApiExamples.UNLIKED))),
		@ApiResponse(responseCode = "400", description = "경로 값 형식 오류, 회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "경로 값 형식 오류", value = BoardApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID 오류", value = BoardApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인 안 함)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = BoardApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "없는 글, 지운 글, 남의 숨겨진 글",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = BoardApiExamples.POST_NOT_FOUND)))
	})
	public RsData<LikeDto> unlike(@LoginMemberId long memberId,
		@Parameter(description = "글 ID", example = "7") @PathVariable long postId) {
		return RsData.of(HttpStatus.OK, "좋아요를 취소했습니다.", boardLikeUseCase.unlike(memberId, postId));
	}
}
