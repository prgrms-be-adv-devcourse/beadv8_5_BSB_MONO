'use client'

import Link from 'next/link'
import { useState } from 'react'

import { BottomNav } from '@/widgets/bottom-nav'
import { useAuthStatus } from '@/features/auth'
import {
  BOARD_CATEGORIES,
  CommentIcon,
  LoginSheet,
  POST_SORTS,
  PostListItem,
  usePosts,
  type BoardCategory,
  type PostSort,
} from '@/entities/board'
import { NoticeBellLink } from '@/entities/notification'
import { cn } from '@/shared/lib'
import {
  BottomSheet,
  BottomSheetOption,
  Button,
  buttonClassName,
  Chip,
  EmptyState,
  FOCUS_RING,
  Icon,
  IconButton,
  Logo,
  Skeleton,
} from '@/shared/ui'

const TABS = [
  { label: '홈', href: '/' },
  { label: '대회 일정', href: '/races' },
  { label: '크루', href: '/crews' },
] as const

// 피그마 머리글 아이콘은 24 그대로 두고 누르는 면만 -m-2 p-2로 키운다
const ICON_LINK =
  '-m-2 inline-flex shrink-0 items-center justify-center rounded-full p-2 text-text-primary hover:bg-bg-subtle [&_svg]:size-6'

// 피그마 커뮤니티 M34 목록 · M34-1 로그인 전 · M34-2 로그인 안내 시트 · M34-3 글 없음 · M34-4 검색 결과 · M34-5 검색 결과 없음 · M34-6 정렬 시트
export function CommunityPage() {
  const auth = useAuthStatus()
  const [category, setCategory] = useState<BoardCategory | null>(null)
  const [sort, setSort] = useState<PostSort>('LATEST')
  const [searching, setSearching] = useState(false)
  const [draft, setDraft] = useState('')
  const [q, setQ] = useState('')
  const [sortOpen, setSortOpen] = useState(false)
  const [loginOpen, setLoginOpen] = useState(false)

  // 검색 화면은 검색어를 보낸 뒤에만 불러온다
  const posts = usePosts(
    { category: searching ? null : category, sort, q: searching ? q : '' },
    { enabled: !searching || q !== '' },
  )
  const sortLabel = POST_SORTS.find((s) => s.value === sort)?.label

  const closeSearch = () => {
    setSearching(false)
    setDraft('')
    setQ('')
  }

  return (
    <>
      {searching ? (
        <form
          role="search"
          className="flex items-center py-2.5 pr-4 pl-0.5"
          onSubmit={(e) => {
            e.preventDefault()
            setQ(draft.trim())
          }}
        >
          <IconButton type="button" aria-label="뒤로 가기" onClick={closeSearch}>
            <Icon name="ChevronLeft" />
          </IconButton>
          <div className="flex h-11 min-w-0 flex-1 items-center gap-2 rounded-full px-4 inset-ring-2 inset-ring-border-brand">
            <Icon name="Search" className="size-4.5 shrink-0" />
            <input
              autoFocus
              type="search"
              aria-label="글 검색"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              className="min-w-0 flex-1 bg-transparent text-body2-regular outline-none [&::-webkit-search-cancel-button]:hidden"
            />
            {draft && (
              <button
                type="button"
                aria-label="지우기"
                className={cn('rounded-full', FOCUS_RING)}
                onClick={() => {
                  setDraft('')
                  setQ('')
                }}
              >
                <Icon name="X" className="size-5" />
              </button>
            )}
          </div>
        </form>
      ) : (
        <>
          <header className="flex shrink-0 items-center gap-3 px-4 py-3">
            <Link href="/" aria-label="홈" className={cn('rounded-sm', FOCUS_RING)}>
              <Logo />
            </Link>
            <div className="flex-1" />
            <button
              type="button"
              aria-label="글 검색"
              className={cn(ICON_LINK, FOCUS_RING)}
              onClick={() => setSearching(true)}
            >
              <Icon name="Search" />
            </button>
            {auth === 'anonymous' ? (
              <Link href="/login?next=%2Fcommunity" className={buttonClassName({ size: 's' })}>
                로그인
              </Link>
            ) : (
              <NoticeBellLink className={ICON_LINK} />
            )}
          </header>
          <nav aria-label="홈 탭" className="flex gap-5 px-4">
            {TABS.map((tab) => (
              <Link
                key={tab.href}
                href={tab.href}
                className="flex h-9 flex-col gap-1.5 pt-1 text-body1-bold text-text-tertiary"
              >
                {tab.label}
                <span className="h-0.5" />
              </Link>
            ))}
            <span
              aria-current="page"
              className="flex h-9 flex-col gap-1.5 pt-1 text-body1-bold text-text-primary"
            >
              커뮤니티
              <span className="h-0.5 bg-text-primary" />
            </span>
          </nav>
        </>
      )}

      <main className="flex flex-1 flex-col">
        {!searching && (
          <div className="flex gap-2 overflow-x-auto px-4 pt-4 pb-2">
            <Chip selected={category === null} onClick={() => setCategory(null)}>
              전체
            </Chip>
            {BOARD_CATEGORIES.map((c) => (
              <Chip
                key={c.value}
                selected={category === c.value}
                onClick={() => setCategory(c.value)}
              >
                {c.label}
              </Chip>
            ))}
          </div>
        )}

        {searching && q === '' ? null : (
          <div
            className={cn(
              'flex items-center px-4 pb-1',
              searching ? 'justify-between pt-4' : 'justify-end pt-1',
            )}
          >
            {searching && posts.data && posts.data.length > 0 && (
              <p className="text-body2-medium text-text-secondary">글 {posts.data.length}개</p>
            )}
            {!(searching && posts.data?.length === 0) && (
              <button
                type="button"
                className={cn(
                  'flex h-9 items-center gap-1 rounded-sm text-body2-medium',
                  FOCUS_RING,
                )}
                onClick={() => setSortOpen(true)}
              >
                {sortLabel}
                <Icon name="ChevronDown" className="size-4" />
              </button>
            )}
          </div>
        )}

        {searching && q === '' ? null : posts.isPending ? (
          <ul role="status" aria-label="글 불러오는 중" className="flex flex-col">
            {[0, 1, 2, 3].map((i) => (
              <li key={i} className="flex flex-col gap-2 border-b border-border-default p-4">
                <Skeleton className="w-16" />
                <Skeleton className="w-3/4" />
                <Skeleton className="w-full" />
              </li>
            ))}
          </ul>
        ) : posts.isError ? (
          <EmptyState
            role="alert"
            title="정보를 불러오지 못했어요"
            description="연결이 잠시 끊겼어요. 네트워크를 확인한 뒤 다시 시도해 주세요."
            action={
              <Button
                variant="secondary"
                onClick={() => void posts.refetch()}
                disabled={posts.isFetching}
                loading={posts.isFetching}
              >
                다시 시도하기
              </Button>
            }
          />
        ) : posts.data.length === 0 ? (
          // 피그마: 검색 결과 없음은 머리글 아래 160, 글 없음은 정렬 줄 아래 120에서 시작
          <div className={searching ? 'pt-40' : 'pt-30'}>
            {searching ? (
              <EmptyState icon="Search" title={`‘${q}’에 맞는 글이 없어요`} />
            ) : (
              <div className="flex flex-col items-center gap-4 px-6 py-10 text-center">
                <CommentIcon className="size-12 text-text-tertiary" strokeWidth={1.71} />
                <p className="text-body1-medium text-text-primary">아직 글이 없어요</p>
              </div>
            )}
          </div>
        ) : (
          <ul>
            {posts.data.map((post) => (
              <PostListItem key={post.postId} post={post} />
            ))}
          </ul>
        )}

        {!searching && (
          <div className="sticky bottom-20 z-10 mt-auto flex h-0 justify-end px-4">
            {auth === 'authenticated' ? (
              <Link
                href="/community/write"
                className={buttonClassName({
                  className: '-translate-y-full rounded-full shadow-md',
                })}
              >
                글쓰기
              </Link>
            ) : (
              <button
                type="button"
                className={buttonClassName({
                  className: '-translate-y-full rounded-full shadow-md',
                })}
                onClick={() => setLoginOpen(true)}
              >
                글쓰기
              </button>
            )}
          </div>
        )}
      </main>
      {!searching && <BottomNav />}

      <BottomSheet open={sortOpen} onOpenChange={setSortOpen} title="정렬">
        {POST_SORTS.map((s) => (
          <BottomSheetOption
            key={s.value}
            selected={sort === s.value}
            onClick={() => {
              setSort(s.value)
              setSortOpen(false)
            }}
          >
            {s.label}
          </BottomSheetOption>
        ))}
      </BottomSheet>
      <LoginSheet open={loginOpen} onOpenChange={setLoginOpen} next="/community/write" />
    </>
  )
}
