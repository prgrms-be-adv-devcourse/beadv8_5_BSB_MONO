'use client'

import Image from 'next/image'
import { useRouter } from 'next/navigation'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { useAuthStatus } from '@/features/auth'
import {
  CommentIcon,
  formatDateTime,
  LoginSheet,
  REPORT_REASONS,
  useComments,
  useDeletePost,
  usePost,
  usePostPhotos,
  useReportPost,
  useToggleLike,
  type ReportReason,
} from '@/entities/board'
import { ApiError } from '@/shared/api'
import { cn } from '@/shared/lib'
import {
  Avatar,
  Badge,
  BottomSheet,
  BottomSheetOption,
  Button,
  Dialog,
  EmptyState,
  FOCUS_RING,
  Icon,
  IconButton,
  Radio,
  RadioGroup,
  Skeleton,
  useToast,
} from '@/shared/ui'

import { CommentSection, countComments } from './CommentSection'

const errorMessage = (e: unknown, fallback: string) =>
  e instanceof ApiError && e.serverMessage ? e.serverMessage : fallback

type Sheet = 'menu' | 'report' | null

// 피그마 M35 글 상세 · M35-1·2·8 더보기 시트 · M35-3 신고 사유 · M35-4 신고 완료 · M35-5 글 삭제 확인 · M35-6 로그인 안내(좋아요) · M35-7 신고로 숨겨진 글 · M35-11 로그인 전
export function CommunityPostPage({ postId }: { postId: number }) {
  const router = useRouter()
  const auth = useAuthStatus()
  const toast = useToast()
  const post = usePost(postId)
  const photos = usePostPhotos(postId)
  const comments = useComments(postId)
  const like = useToggleLike(postId)
  const report = useReportPost(postId)
  const remove = useDeletePost(postId)
  const [sheet, setSheet] = useState<Sheet>(null)
  const [reason, setReason] = useState<ReportReason | null>(null)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [loginOpen, setLoginOpen] = useState(false)
  const authenticated = auth === 'authenticated'
  const next = `/community/${postId}`

  if (post.isPending) {
    return (
      <>
        <AppHeader type="sub" title="커뮤니티" backHref="/community" />
        <main role="status" aria-label="글 불러오는 중" className="flex flex-col gap-3 p-4">
          <Skeleton className="w-16" />
          <Skeleton className="h-6 w-3/4" />
          <Skeleton className="w-full" />
          <Skeleton className="w-full" />
        </main>
      </>
    )
  }
  if (post.isError) {
    const notFound = post.error instanceof ApiError && post.error.status === 404
    return (
      <>
        <AppHeader type="sub" title="커뮤니티" backHref="/community" />
        <main className="flex flex-1 items-center justify-center px-4 pb-16">
          <EmptyState
            role="alert"
            icon={notFound ? 'Search' : 'CircleAlert'}
            title={notFound ? '글을 찾을 수 없어요' : '글을 불러오지 못했어요'}
            action={
              notFound ? undefined : (
                <Button
                  variant="secondary"
                  loading={post.isFetching}
                  onClick={() => void post.refetch()}
                >
                  다시 시도하기
                </Button>
              )
            }
          />
        </main>
      </>
    )
  }

  const p = post.data
  const hidden = p.status === 'HIDDEN'
  const writer = p.writer

  const onLike = () => {
    if (!authenticated) return setLoginOpen(true)
    like.mutate(p.liked, {
      onError: (e) =>
        toast({ message: errorMessage(e, '좋아요를 처리하지 못했어요'), tone: 'error' }),
    })
  }

  const onReport = () => {
    if (!reason) return
    report.mutate(reason, {
      onSuccess: () => {
        setSheet(null)
        setReason(null)
        toast({ message: '신고했어요' })
      },
      onError: (e) => toast({ message: errorMessage(e, '신고하지 못했어요'), tone: 'error' }),
    })
  }

  return (
    <>
      <AppHeader
        type="sub"
        title="커뮤니티"
        backHref="/community"
        action={
          <IconButton aria-label="더보기" onClick={() => setSheet('menu')}>
            <Icon name="MoreVertical" />
          </IconButton>
        }
      />
      <main className="flex flex-1 flex-col">
        {hidden && (
          <p className="flex items-center gap-2 bg-bg-subtle px-4 py-3 text-body2-medium text-text-secondary">
            <Icon name="CircleAlert" className="size-5" />
            신고로 숨겨진 글이에요
          </p>
        )}
        <article className="flex flex-col gap-4 px-4 pt-5 pb-4">
          <div className="flex flex-col gap-1">
            <span className="text-caption-medium text-text-secondary">{p.categoryLabel}</span>
            <h1 className="text-title2 break-words">{p.title}</h1>
          </div>
          <div className="flex items-center gap-2">
            <Avatar size="m" name={writer.withdrawn ? undefined : writer.nickname} />
            <div className="flex flex-col">
              <span className="flex items-center gap-2">
                <span className={cn('text-body2-bold', writer.withdrawn && 'text-text-tertiary')}>
                  {writer.nickname}
                </span>
                {writer.seller && <Badge>판매자</Badge>}
              </span>
              <span className="text-caption-regular text-text-tertiary">
                {formatDateTime(p.createdAt)}
                {p.edited && ' · 수정됨'}
              </span>
            </div>
          </div>
          <p className="text-body1-regular break-words whitespace-pre-wrap">{p.content}</p>
          {photos.data && photos.data.length > 0 && (
            <ul
              className={cn('grid gap-2', photos.data.length > 1 ? 'grid-cols-2' : 'grid-cols-1')}
            >
              {photos.data.map((photo) => (
                <li
                  key={photo.fileId}
                  className="relative aspect-square overflow-hidden rounded-md"
                >
                  <Image src={photo.url} alt="" fill sizes="280px" className="object-cover" />
                </li>
              ))}
            </ul>
          )}
          <div className="flex items-center gap-4">
            <button
              type="button"
              aria-pressed={p.liked}
              onClick={onLike}
              disabled={like.isPending}
              className={cn(
                'flex h-9.5 items-center gap-1 rounded-full px-3.5 text-body2-medium inset-ring inset-ring-border-default',
                FOCUS_RING,
              )}
            >
              <Icon
                name="Heart"
                className={cn('size-5', p.liked && 'fill-current text-text-danger')}
              />
              좋아요 {p.likeCount}
            </button>
            <span className="flex items-center gap-1 text-body2-regular text-text-secondary">
              <CommentIcon className="size-5" />
              댓글 {countComments(comments.data ?? [])}
            </span>
          </div>
        </article>
        <div className="h-2 bg-bg-subtle" />
        <CommentSection postId={postId} authenticated={authenticated} locked={hidden} next={next} />
      </main>

      <BottomSheet
        open={sheet === 'menu'}
        onOpenChange={(open) => !open && setSheet(null)}
        title="더보기"
      >
        {p.mine ? (
          <>
            {!hidden && (
              <BottomSheetOption onClick={() => router.push(`/community/${postId}/edit`)}>
                수정하기
              </BottomSheetOption>
            )}
            <BottomSheetOption
              onClick={() => {
                setSheet(null)
                setDeleteOpen(true)
              }}
            >
              삭제하기
            </BottomSheetOption>
          </>
        ) : (
          <BottomSheetOption
            onClick={() =>
              authenticated ? setSheet('report') : (setSheet(null), setLoginOpen(true))
            }
          >
            신고하기
          </BottomSheetOption>
        )}
      </BottomSheet>
      <BottomSheet
        open={sheet === 'report'}
        onOpenChange={(open) => !open && setSheet(null)}
        title="신고 사유"
        footer={
          <Button size="l" disabled={!reason} loading={report.isPending} onClick={onReport}>
            신고하기
          </Button>
        }
      >
        <RadioGroup
          aria-label="신고 사유"
          value={reason ?? ''}
          onValueChange={(value) => setReason(value as ReportReason)}
          className="gap-0 px-2"
        >
          {REPORT_REASONS.map((r) => (
            <Radio key={r.value} value={r.value} label={r.label} className="h-13" />
          ))}
        </RadioGroup>
      </BottomSheet>
      <Dialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        type="danger"
        title="글을 삭제할까요?"
        body="삭제한 글과 사진은 되돌릴 수 없어요."
        confirmLabel="삭제"
        confirming={remove.isPending}
        onConfirm={() =>
          remove.mutate(undefined, {
            onSuccess: () => router.replace('/community'),
            onError: (e) =>
              toast({ message: errorMessage(e, '글을 지우지 못했어요'), tone: 'error' }),
          })
        }
      />
      <LoginSheet open={loginOpen} onOpenChange={setLoginOpen} next={next} />
    </>
  )
}
