// 게시판 서비스(board-service)의 응답 모양. 글 상세·댓글·좋아요는 ApiV1BoardController의 DTO와 같다.
// 목록·검색·신고·관리자 API는 백엔드에 아직 없어 프론트가 경로를 정했다(BUK-118 보고 참고).

export type BoardCategory = 'FREE' | 'RACE_REVIEW' | 'QUESTION' | 'RUN_TOGETHER'

export const BOARD_CATEGORIES: { value: BoardCategory; label: string }[] = [
  { value: 'FREE', label: '자유' },
  { value: 'RACE_REVIEW', label: '대회 후기' },
  { value: 'QUESTION', label: '질문' },
  { value: 'RUN_TOGETHER', label: '같이 뛰어요' },
]

export type PostSort = 'LATEST' | 'POPULAR'

export const POST_SORTS: { value: PostSort; label: string }[] = [
  { value: 'LATEST', label: '최신순' },
  { value: 'POPULAR', label: '인기순' },
]

export type BoardWriter = {
  /** 탈퇴한 회원이면 「탈퇴한 회원」 */
  nickname: string
  seller: boolean
  withdrawn: boolean
}

/** 목록·검색 결과의 글 한 줄 (GET /api/v1/board/posts) */
export type PostSummary = {
  postId: number
  category: BoardCategory
  categoryLabel: string
  title: string
  /** 본문 앞부분 */
  excerpt: string
  writer: BoardWriter
  createdAt: string
  likeCount: number
  commentCount: number
  /** 첫 번째 사진 주소. 사진이 없으면 null */
  thumbnailUrl: string | null
}

/** 글 상세 (PostDetailDto) */
export type PostDetail = {
  postId: number
  category: BoardCategory
  categoryLabel: string
  title: string
  content: string
  /** HIDDEN은 신고로 숨겨진 글(쓴 사람에게만 보인다) */
  status: 'VISIBLE' | 'HIDDEN'
  writer: BoardWriter
  mine: boolean
  edited: boolean
  likeCount: number
  liked: boolean
  createdAt: string
}

/** 댓글 또는 답글 (CommentDto). 지운 댓글은 content · writer가 null */
export type BoardComment = {
  commentId: number
  parentId: number | null
  status: 'VISIBLE' | 'DELETED'
  content: string | null
  writer: BoardWriter | null
  mine: boolean
  edited: boolean
  createdAt: string
  replies: BoardComment[]
}

export type Like = { likeCount: number; liked: boolean }

/** 글 쓰기·고치기 요청 (PostSaveRequestDto) */
export type PostSaveRequest = {
  category: BoardCategory
  title: string
  content: string
  fileIds: number[]
}

export type ReportReason = 'AD' | 'ABUSE' | 'OBSCENE' | 'OTHER'

export const REPORT_REASONS: { value: ReportReason; label: string }[] = [
  { value: 'AD', label: '광고' },
  { value: 'ABUSE', label: '욕설·비방' },
  { value: 'OBSCENE', label: '음란' },
  { value: 'OTHER', label: '기타' },
]

export const TITLE_MAX = 50
export const CONTENT_MAX = 5000
export const COMMENT_MAX = 500
export const PHOTO_MAX = 5

/** 글에 붙은 사진 한 장 */
export type PostPhoto = { fileId: number; url: string }

// 관리자 신고 관리 (프론트가 정한 API)
export type ReportTargetType = 'POST' | 'COMMENT'
export type ReportStatus = 'HIDDEN' | 'RESTORED' | 'DELETED'

export type AdminReport = {
  targetType: ReportTargetType
  targetId: number
  writerNickname: string
  /** 글이면 제목, 댓글이면 댓글 내용 */
  content: string
  /** 댓글이면 달린 글의 제목 */
  postTitle: string | null
  reportCount: number
  reasons: { reason: ReportReason; count: number }[]
  status: ReportStatus
  /** 되살리거나 지운 시각. 아직 처리하지 않았으면 없다 */
  handledAt?: string | null
}

export type AdminReportList = {
  hiddenCount: number
  pendingReportCount: number
  handledTodayCount: number
  items: AdminReport[]
}
