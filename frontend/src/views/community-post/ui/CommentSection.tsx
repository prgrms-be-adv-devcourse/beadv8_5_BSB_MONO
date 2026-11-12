'use client'

import { useState } from 'react'

import {
  COMMENT_MAX,
  formatRelative,
  LoginSheet,
  useComments,
  useCreateComment,
  useDeleteComment,
  useUpdateComment,
  type BoardComment,
} from '@/entities/board'
import { ApiError } from '@/shared/api'
import { cn } from '@/shared/lib'
import { Avatar, Badge, Dialog, FOCUS_RING, Icon, IconButton, useToast } from '@/shared/ui'

import { MenuSheet, MenuSheetAction } from './MenuSheet'

type Mode =
  { kind: 'new' } | { kind: 'reply'; parent: BoardComment } | { kind: 'edit'; target: BoardComment }

const errorMessage = (e: unknown, fallback: string) =>
  e instanceof ApiError && e.serverMessage ? e.serverMessage : fallback

export function countComments(comments: BoardComment[]) {
  return comments.reduce((sum, c) => sum + 1 + c.replies.length, 0)
}

// 피그마 M35 글 상세의 댓글 · M35-9 더보기 시트(내 댓글) · M35-10 댓글 삭제 확인 · M35-11 로그인 전 · M35-7 숨겨진 글(댓글 못 닮)
export function CommentSection({
  postId,
  authenticated,
  locked,
  next,
}: {
  postId: number
  authenticated: boolean
  /** 신고로 숨겨진 글은 댓글을 달 수 없다 */
  locked: boolean
  next: string
}) {
  const comments = useComments(postId)
  const create = useCreateComment(postId)
  const update = useUpdateComment()
  const remove = useDeleteComment()
  const toast = useToast()
  const [mode, setMode] = useState<Mode>({ kind: 'new' })
  const [text, setText] = useState('')
  const [menu, setMenu] = useState<BoardComment | null>(null)
  const [deleting, setDeleting] = useState<BoardComment | null>(null)
  const [loginOpen, setLoginOpen] = useState(false)

  const reset = () => {
    setMode({ kind: 'new' })
    setText('')
  }

  const submit = () => {
    const content = text.trim()
    if (!content) return
    const done = {
      onSuccess: reset,
      onError: (e: unknown) =>
        toast({ message: errorMessage(e, '댓글을 올리지 못했어요'), tone: 'error' }),
    }
    if (mode.kind === 'edit') {
      update.mutate({ commentId: mode.target.commentId, content }, done)
    } else {
      create.mutate(
        { content, parentId: mode.kind === 'reply' ? mode.parent.commentId : undefined },
        done,
      )
    }
  }

  const list = comments.data ?? []
  const placeholder =
    mode.kind === 'reply' ? '답글 남기기' : mode.kind === 'edit' ? '댓글 수정하기' : '댓글 남기기'

  return (
    <>
      <section aria-label="댓글" className="flex flex-col gap-5 px-4 py-5">
        <h2 className="text-body1-bold">댓글 {countComments(list)}</h2>
        {comments.isError && (
          <p role="alert" className="text-body2-regular text-text-secondary">
            댓글을 불러오지 못했어요
          </p>
        )}
        <ul className="flex flex-col gap-5">
          {list.map((comment) => (
            <li key={comment.commentId} className="flex flex-col gap-5">
              <CommentRow
                comment={comment}
                onReply={
                  locked
                    ? undefined
                    : () =>
                        authenticated
                          ? setMode({ kind: 'reply', parent: comment })
                          : setLoginOpen(true)
                }
                onMenu={() => setMenu(comment)}
              />
              {comment.replies.map((reply) => (
                <div key={reply.commentId} className="ml-11">
                  <CommentRow comment={reply} onMenu={() => setMenu(reply)} />
                </div>
              ))}
            </li>
          ))}
        </ul>
      </section>

      <div className="sticky bottom-0 z-10 mt-auto border-t border-border-default bg-bg-surface px-4 py-3">
        {locked ? (
          <p className="flex h-11 items-center rounded-full bg-bg-subtle px-4 text-body2-regular text-text-disabled">
            댓글을 달 수 없는 글이에요
          </p>
        ) : !authenticated ? (
          <button
            type="button"
            className={cn(
              'h-11 w-full rounded-full bg-bg-subtle text-body2-medium text-text-brand',
              FOCUS_RING,
            )}
            onClick={() => setLoginOpen(true)}
          >
            로그인하고 댓글 남기기
          </button>
        ) : (
          <form
            className="flex items-center gap-3"
            onSubmit={(e) => {
              e.preventDefault()
              submit()
            }}
          >
            <input
              aria-label={placeholder}
              placeholder={placeholder}
              value={text}
              maxLength={COMMENT_MAX}
              onChange={(e) => setText(e.target.value)}
              className="h-11 min-w-0 flex-1 rounded-full bg-bg-subtle px-4 text-body2-regular outline-none placeholder:text-text-disabled focus-visible:inset-ring-2 focus-visible:inset-ring-border-brand"
            />
            {mode.kind !== 'new' && (
              <IconButton type="button" size="s" aria-label="취소" onClick={reset}>
                <Icon name="X" />
              </IconButton>
            )}
            <button
              type="submit"
              disabled={!text.trim() || create.isPending || update.isPending}
              className={cn(
                'rounded-sm px-1 text-body2-medium text-text-secondary enabled:text-text-brand disabled:text-text-disabled',
                FOCUS_RING,
              )}
            >
              {mode.kind === 'edit' ? '저장' : '등록'}
            </button>
          </form>
        )}
      </div>

      <MenuSheet open={menu !== null} onOpenChange={(open) => !open && setMenu(null)} label="댓글">
        <MenuSheetAction
          onClick={() => {
            if (menu) {
              setMode({ kind: 'edit', target: menu })
              setText(menu.content ?? '')
            }
            setMenu(null)
          }}
        >
          수정하기
        </MenuSheetAction>
        <MenuSheetAction
          danger
          onClick={() => {
            setDeleting(menu)
            setMenu(null)
          }}
        >
          삭제하기
        </MenuSheetAction>
      </MenuSheet>
      <Dialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        type="danger"
        title="댓글을 삭제할까요?"
        body="삭제한 댓글은 되돌릴 수 없어요."
        confirmLabel="삭제"
        confirming={remove.isPending}
        onConfirm={() => {
          if (!deleting) return
          remove.mutate(deleting.commentId, {
            onSuccess: () => setDeleting(null),
            onError: (e) =>
              toast({ message: errorMessage(e, '댓글을 지우지 못했어요'), tone: 'error' }),
          })
        }}
      />
      <LoginSheet open={loginOpen} onOpenChange={setLoginOpen} next={next} />
    </>
  )
}

function CommentRow({
  comment,
  onReply,
  onMenu,
}: {
  comment: BoardComment
  onReply?: () => void
  onMenu: () => void
}) {
  if (comment.status === 'DELETED') {
    return <p className="text-body2-regular text-text-tertiary">삭제된 댓글이에요</p>
  }
  const writer = comment.writer
  const gone = writer?.withdrawn ?? false
  return (
    <div className="flex gap-3">
      <Avatar size="s" name={gone ? undefined : writer?.nickname} className="mt-0.5" />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <div className="flex items-center gap-2">
          <span className={cn('text-body2-bold', gone && 'text-text-tertiary')}>
            {writer?.nickname}
          </span>
          {writer?.seller && <Badge>판매자</Badge>}
        </div>
        <p className="text-body2-regular break-words whitespace-pre-wrap">{comment.content}</p>
        <div className="flex items-center gap-3 text-caption-regular text-text-tertiary">
          <span>
            {formatRelative(comment.createdAt)}
            {comment.edited && ' · 수정됨'}
          </span>
          {onReply && (
            <button
              type="button"
              className={cn('rounded-sm text-caption-medium', FOCUS_RING)}
              onClick={onReply}
            >
              답글 달기
            </button>
          )}
        </div>
      </div>
      {comment.mine && (
        <IconButton
          size="s"
          aria-label="댓글 더보기"
          className="-m-1.5 [&_svg]:size-5"
          onClick={onMenu}
        >
          <Icon name="MoreVertical" />
        </IconButton>
      )}
    </div>
  )
}
