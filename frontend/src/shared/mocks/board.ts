import { http, HttpResponse } from 'msw'

/**
 * 커뮤니티(게시판) 목. 글 상세·댓글·좋아요는 board-service ApiV1BoardController의 경로와 DTO를 따른다.
 * 목록·검색·신고·관리자 API는 백엔드에 아직 없어 프론트가 정한 경로다.
 * 로그인 여부는 Authorization 헤더로 가른다. 로그인하면 달리는곰의 글(1번)이 「내 글」, 4번이 「신고로 숨겨진 내 글」이다.
 * 상태는 모듈 메모리에 둔다(가짜 응답 모드 전용). 테스트는 resetBoardMock()으로 되돌린다.
 */

type Category = 'FREE' | 'RACE_REVIEW' | 'QUESTION' | 'RUN_TOGETHER'
const LABEL: Record<Category, string> = {
  FREE: '자유',
  RACE_REVIEW: '대회 후기',
  QUESTION: '질문',
  RUN_TOGETHER: '같이 뛰어요',
}

type MockPost = {
  id: number
  category: Category
  title: string
  content: string
  nickname: string
  seller?: boolean
  withdrawn?: boolean
  minutesAgo: number
  likes: number
  edited?: boolean
  hidden?: boolean
  /** 로그인하면 내 글이 되는 글 */
  ownedByDemo?: boolean
  photos: string[]
}

type MockComment = {
  id: number
  parentId: number | null
  content: string | null
  nickname: string
  seller?: boolean
  withdrawn?: boolean
  minutesAgo: number
  edited?: boolean
  mine?: boolean
}

const JTBC = '/mock/races/jtbc.webp'
const COOKIE = '/mock/races/cookierun.webp'
const YOUTH = '/mock/races/youth10k.webp'

const SEED_POSTS: MockPost[] = [
  {
    id: 1,
    category: 'RACE_REVIEW',
    title: 'JTBC 서울마라톤 첫 풀코스 완주했어요',
    content:
      '30km 지나서 다리가 풀렸는데 응원 소리 듣고 끝까지 갔어요.\n\n급수대는 5km마다 있었고, 25km 지점에서 바나나랑 에너지젤을 받았어요. 출발 그룹은 기록 순으로 나눠서 처음 2km는 조금 붐볐어요.\n\n다음엔 4시간 30분 안에 들어오는 게 목표예요.',
    nickname: '달리는곰',
    minutesAgo: 12,
    likes: 24,
    edited: true,
    ownedByDemo: true,
    photos: [JTBC, COOKIE],
  },
  {
    id: 2,
    category: 'RUN_TOGETHER',
    title: '토요일 아침 7시 여의도 10km 같이 뛰실 분',
    content: '6분 페이스로 천천히 뛰어요. 여의나루역 2번 출구에서 만나요.',
    nickname: '새벽러너',
    minutesAgo: 38,
    likes: 8,
    photos: [],
  },
  {
    id: 3,
    category: 'QUESTION',
    title: '하프 처음인데 에너지젤 몇 개 챙겨야 하나요?',
    content: '10km까지만 뛰어 봤어요. 하프는 처음이라 보급을 어떻게 할지 모르겠어요.',
    nickname: '초보주자',
    minutesAgo: 60,
    likes: 3,
    photos: [],
  },
  {
    id: 4,
    category: 'FREE',
    title: '11월 대회 키트 받으신 분 사이즈 어때요?',
    content: '정사이즈로 주문했는데 조금 크게 나온 것 같아요. 다른 분들은 어떠셨나요?',
    nickname: '크루런 러닝샵',
    seller: true,
    minutesAgo: 180,
    likes: 15,
    photos: [YOUTH],
  },
  {
    id: 5,
    category: 'RACE_REVIEW',
    title: '긍정 하프 마라톤 코스 정리',
    content: '오르막은 7km 지점 한 번뿐이고 나머지는 평지예요. 화장실은 출발점에 많아요.',
    nickname: '탈퇴한 회원',
    withdrawn: true,
    minutesAgo: 60 * 30,
    likes: 41,
    photos: [YOUTH],
  },
  {
    id: 6,
    category: 'FREE',
    title: '러닝화 최저가 링크 모음, 지금 바로 확인하세요',
    content: '광고성 글 예시예요.',
    nickname: '광고왕',
    minutesAgo: 60 * 5,
    likes: 0,
    hidden: true,
    ownedByDemo: true,
    photos: [],
  },
]

const SEED_COMMENTS: MockComment[] = [
  {
    id: 1,
    parentId: null,
    content: '완주 축하드려요! 25km 보급 위치는 내년에도 같다고 들었어요.',
    nickname: '크루런 러닝샵',
    seller: true,
    minutesAgo: 10,
  },
  {
    id: 2,
    parentId: 1,
    content: '감사합니다. 내년에도 나갈게요!',
    nickname: '달리는곰',
    minutesAgo: 8,
    mine: true,
  },
  { id: 3, parentId: null, content: null, nickname: '', minutesAgo: 7 },
  {
    id: 4,
    parentId: 3,
    content: '30km 넘어서가 제일 힘들죠. 저도 거기서 걸었어요.',
    nickname: '새벽러너',
    minutesAgo: 5,
    edited: true,
  },
  {
    id: 5,
    parentId: null,
    content: '첫 풀코스에 이 기록이면 대단해요.',
    nickname: '탈퇴한 회원',
    withdrawn: true,
    minutesAgo: 60 * 30,
  },
]

type MockReport = {
  targetType: 'POST' | 'COMMENT'
  targetId: number
  writerNickname: string
  content: string
  postTitle: string | null
  reportCount: number
  reasons: { reason: string; count: number }[]
  status: 'HIDDEN' | 'RESTORED' | 'DELETED'
  handledAt?: string | null
}

const SEED_REPORTS: MockReport[] = [
  {
    targetType: 'POST',
    targetId: 6,
    writerNickname: '광고왕',
    content: '러닝화 최저가 링크 모음, 지금 바로…',
    postTitle: null,
    reportCount: 7,
    reasons: [{ reason: 'AD', count: 7 }],
    status: 'HIDDEN',
  },
  {
    targetType: 'COMMENT',
    targetId: 11,
    writerNickname: '달리자123',
    content: '그 기록으로 풀코스요? 웃기네요',
    postTitle: 'JTBC 서울마라톤 첫 풀코스 완주했어요',
    reportCount: 5,
    reasons: [
      { reason: 'ABUSE', count: 4 },
      { reason: 'OTHER', count: 1 },
    ],
    status: 'HIDDEN',
  },
  {
    targetType: 'POST',
    targetId: 2,
    writerNickname: '새벽러너',
    content: '토요일 아침 7시 여의도 10km 같이 뛰실 분',
    postTitle: null,
    reportCount: 5,
    reasons: [{ reason: 'OTHER', count: 5 }],
    status: 'RESTORED',
  },
]

let posts = structuredClone(SEED_POSTS)
let comments = structuredClone(SEED_COMMENTS)
let reports = structuredClone(SEED_REPORTS)
let liked = new Set<number>()
let nextId = 100

export function resetBoardMock() {
  posts = structuredClone(SEED_POSTS)
  comments = structuredClone(SEED_COMMENTS)
  reports = structuredClone(SEED_REPORTS)
  liked = new Set()
  nextId = 100
}

const ok = <T>(message: string, data: T, status = 200) =>
  HttpResponse.json({ status, message, data }, { status })

const fail = (status: number, code: string, message: string) =>
  HttpResponse.json({ status, code, message, data: null }, { status })

const loggedIn = (request: Request) => request.headers.has('Authorization')
const at = (minutesAgo: number) => new Date(Date.now() - minutesAgo * 60_000).toISOString()
const writerOf = (p: { nickname: string; seller?: boolean; withdrawn?: boolean }) => ({
  nickname: p.nickname,
  seller: p.seller ?? false,
  withdrawn: p.withdrawn ?? false,
})
const commentCount = (postId: number) => (postId === 1 ? comments.length : 0)

function summary(p: MockPost) {
  return {
    postId: p.id,
    category: p.category,
    categoryLabel: LABEL[p.category],
    title: p.title,
    excerpt: p.content.replace(/\s+/g, ' '),
    writer: writerOf(p),
    createdAt: at(p.minutesAgo),
    likeCount: p.likes + (liked.has(p.id) ? 1 : 0),
    commentCount: commentCount(p.id),
    thumbnailUrl: p.photos[0] ?? null,
  }
}

function detail(p: MockPost, request: Request) {
  const login = loggedIn(request)
  return {
    postId: p.id,
    category: p.category,
    categoryLabel: LABEL[p.category],
    title: p.title,
    content: p.content,
    status: p.hidden ? 'HIDDEN' : 'VISIBLE',
    writer: writerOf(p),
    mine: login && Boolean(p.ownedByDemo),
    edited: Boolean(p.edited),
    likeCount: p.likes + (liked.has(p.id) ? 1 : 0),
    liked: login && liked.has(p.id),
    createdAt: at(p.minutesAgo),
  }
}

function commentTree(request: Request) {
  const login = loggedIn(request)
  const dto = (c: MockComment, replies: unknown[]) => ({
    commentId: c.id,
    parentId: c.parentId,
    status: c.content === null ? 'DELETED' : 'VISIBLE',
    content: c.content,
    writer: c.content === null ? null : writerOf(c),
    mine: login && Boolean(c.mine),
    edited: Boolean(c.edited),
    createdAt: at(c.minutesAgo),
    replies,
  })
  return comments
    .filter((c) => c.parentId === null)
    .map((root) =>
      dto(
        root,
        comments.filter((c) => c.parentId === root.id).map((c) => dto(c, [])),
      ),
    )
}

const postOf = (id: unknown) => posts.find((p) => p.id === Number(id))

export const boardHandlers = [
  http.get('*/api/v1/board/posts', ({ request }) => {
    const url = new URL(request.url)
    const category = url.searchParams.get('category')
    const q = (url.searchParams.get('q') ?? '').trim()
    const popular = url.searchParams.get('sort') === 'POPULAR'
    const found = posts
      .filter((p) => !p.hidden)
      .filter((p) => !category || p.category === category)
      .filter((p) => !q || p.title.includes(q) || p.content.includes(q))
      .sort((a, b) => (popular ? b.likes - a.likes : a.minutesAgo - b.minutesAgo))
    return ok('글 목록을 조회했습니다.', found.map(summary))
  }),
  http.post('*/api/v1/board/posts', async ({ request }) => {
    if (!loggedIn(request)) return fail(401, 'AUTH_REQUIRED', '로그인이 필요합니다.')
    const body = (await request.json()) as {
      category?: Category
      title: string
      content: string
      fileIds?: number[]
    }
    nextId += 1
    posts.unshift({
      id: nextId,
      category: body.category ?? 'FREE',
      title: body.title,
      content: body.content,
      nickname: '달리는곰',
      minutesAgo: 0,
      likes: 0,
      ownedByDemo: true,
      photos: (body.fileIds ?? []).map(() => JTBC),
    })
    return ok('글을 등록했습니다.', { postId: nextId }, 201)
  }),
  http.get('*/api/v1/board/posts/:postId', ({ params, request }) => {
    const post = postOf(params.postId)
    // 숨겨진 글은 쓴 사람에게만 보인다
    if (!post || (post.hidden && !(loggedIn(request) && post.ownedByDemo))) {
      return fail(404, 'BOARD_POST_NOT_FOUND', '글을 찾을 수 없습니다.')
    }
    return ok('글을 조회했습니다.', detail(post, request))
  }),
  http.put('*/api/v1/board/posts/:postId', async ({ params, request }) => {
    const post = postOf(params.postId)
    if (!post) return fail(404, 'BOARD_POST_NOT_FOUND', '글을 찾을 수 없습니다.')
    if (post.hidden) return fail(403, 'BOARD_POST_HIDDEN', '신고로 숨겨진 글은 고칠 수 없습니다.')
    const body = (await request.json()) as { category?: Category; title: string; content: string }
    Object.assign(post, {
      category: body.category ?? post.category,
      title: body.title,
      content: body.content,
      edited: true,
    })
    return ok('글을 수정했습니다.', null)
  }),
  http.delete('*/api/v1/board/posts/:postId', ({ params }) => {
    posts = posts.filter((p) => p.id !== Number(params.postId))
    return ok('글을 삭제했습니다.', null)
  }),
  http.get('*/api/v1/board/posts/:postId/comments', ({ params, request }) =>
    ok('댓글을 조회했습니다.', params.postId === '1' ? commentTree(request) : []),
  ),
  http.post('*/api/v1/board/posts/:postId/comments', async ({ request }) => {
    if (!loggedIn(request)) return fail(401, 'AUTH_REQUIRED', '로그인이 필요합니다.')
    const body = (await request.json()) as { content: string; parentId?: number }
    nextId += 1
    comments.push({
      id: nextId,
      parentId: body.parentId ?? null,
      content: body.content,
      nickname: '달리는곰',
      minutesAgo: 0,
      mine: true,
    })
    return ok('댓글을 등록했습니다.', { commentId: nextId }, 201)
  }),
  http.put('*/api/v1/board/comments/:commentId', async ({ params, request }) => {
    const target = comments.find((c) => c.id === Number(params.commentId))
    if (!target) return fail(404, 'BOARD_COMMENT_NOT_FOUND', '댓글을 찾을 수 없습니다.')
    target.content = ((await request.json()) as { content: string }).content
    target.edited = true
    return ok('댓글을 수정했습니다.', null)
  }),
  http.delete('*/api/v1/board/comments/:commentId', ({ params }) => {
    const id = Number(params.commentId)
    // 답글이 달린 댓글은 「삭제된 댓글이에요」로 남기고, 아니면 목록에서 뺀다
    if (comments.some((c) => c.parentId === id)) {
      const target = comments.find((c) => c.id === id)
      if (target) target.content = null
    } else {
      comments = comments.filter((c) => c.id !== id)
    }
    return ok('댓글을 삭제했습니다.', null)
  }),
  http.put('*/api/v1/board/posts/:postId/like', ({ params }) => {
    liked.add(Number(params.postId))
    const post = postOf(params.postId)
    return ok('좋아요를 눌렀습니다.', { likeCount: (post?.likes ?? 0) + 1, liked: true })
  }),
  http.delete('*/api/v1/board/posts/:postId/like', ({ params }) => {
    liked.delete(Number(params.postId))
    const post = postOf(params.postId)
    return ok('좋아요를 취소했습니다.', { likeCount: post?.likes ?? 0, liked: false })
  }),
  // 프론트가 정한 API: 글 신고
  http.post('*/api/v1/board/posts/:postId/report', ({ request }) =>
    loggedIn(request)
      ? ok('신고했습니다.', null, 201)
      : fail(401, 'AUTH_REQUIRED', '로그인이 필요합니다.'),
  ),
  // 프론트가 정한 API: 관리자 신고 관리
  http.get('*/api/v1/board/admin/reports', () =>
    ok('신고 목록을 조회했습니다.', {
      hiddenCount: reports.filter((r) => r.status === 'HIDDEN').length,
      pendingReportCount: 12,
      handledTodayCount: 2 + reports.filter((r) => r.status === 'DELETED').length,
      items: reports,
    }),
  ),
  http.post('*/api/v1/board/admin/reports/:targets/:targetId/:action', ({ params }) => {
    const target = reports.find(
      (r) =>
        `${r.targetType.toLowerCase()}s` === params.targets &&
        r.targetId === Number(params.targetId),
    )
    if (!target) return fail(404, 'BOARD_REPORT_NOT_FOUND', '신고를 찾을 수 없습니다.')
    target.status = params.action === 'restore' ? 'RESTORED' : 'DELETED'
    target.handledAt = new Date().toISOString()
    return ok('처리했습니다.', null)
  }),
  // 글에 붙은 사진 (file-service)
  http.get('*/api/v1/file/files', ({ request }) => {
    const url = new URL(request.url)
    if (url.searchParams.get('refType') !== 'BoardPost') return undefined
    const post = postOf(url.searchParams.get('refId'))
    return ok(
      '파일 목록을 조회했습니다.',
      (post?.photos ?? []).map((photoUrl, index) => ({ id: index + 1, url: photoUrl })),
    )
  }),
]
