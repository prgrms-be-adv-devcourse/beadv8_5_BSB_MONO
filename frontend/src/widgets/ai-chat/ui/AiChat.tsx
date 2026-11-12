'use client'

import Link from 'next/link'
import { useEffect, useRef, useState, type KeyboardEvent } from 'react'

import { safeInternalPath } from '@/features/auth'
import {
  aiErrorMessage,
  useAiChat,
  useAiClear,
  type AiAnswer,
  type AiScreen,
  type AiSource,
} from '@/entities/ai-assistant'
import { cn } from '@/shared/lib'
import { Alert, Button, Chip, Logo, Spinner } from '@/shared/ui'

/** 서버가 받는 질문 최대 글자 수 (AiChatRequestDto) */
const MAX_LENGTH = 1000

type Turn =
  | { id: number; role: 'user'; text: string }
  | { id: number; role: 'ai'; answer: AiAnswer }
  | { id: number; role: 'error'; text: string }

export type AiChatProps = {
  screen: AiScreen
  /** 첫 화면 인사 (예: 안녕하세요, 운영자님) */
  greeting: string
  placeholder: string
  /** 입력칸 아래 작은 안내 (무엇으로 답하는지) */
  scopeNote: string
  /** 첫 화면의 추천 질문 */
  suggestions: string[]
  /** 대화 화면 맨 아래 작은 안내 */
  footnote: string
}

/** 2026-11-03T14:03:00 → 11월 3일 */
function dayText(iso: string) {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '' : `${d.getMonth() + 1}월 ${d.getDate()}일`
}

function sourceLine(source: AiSource) {
  const day = source.asOf ? dayText(source.asOf) : ''
  return day ? `${source.screen} 기준 · ${day}` : null
}

function BrandMark({ className }: { className?: string }) {
  return (
    <span
      aria-hidden
      className={cn(
        'flex shrink-0 items-center justify-center rounded-md bg-bg-brand text-body2-bold text-text-on-brand italic',
        className,
      )}
    >
      크
    </span>
  )
}

function Composer({
  value,
  onChange,
  onSend,
  pending,
  placeholder,
  scopeNote,
  rows,
}: {
  value: string
  onChange: (value: string) => void
  onSend: () => void
  pending: boolean
  placeholder: string
  scopeNote: string
  rows: number
}) {
  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    // 한글 조합 중 Enter는 글자 확정이라 보내지 않는다
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault()
      onSend()
    }
  }
  return (
    <div className="flex flex-col gap-3 rounded-xl bg-bg-surface p-4 shadow-sm inset-ring inset-ring-border-default">
      <textarea
        aria-label="질문"
        value={value}
        rows={rows}
        maxLength={MAX_LENGTH}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
        onKeyDown={handleKeyDown}
        className="w-full resize-none bg-transparent text-body1-regular text-text-primary outline-none placeholder:text-text-tertiary"
      />
      <div className="flex items-center justify-between gap-3">
        <span className="text-caption-regular text-text-tertiary">{scopeNote}</span>
        <Button size="s" onClick={onSend} disabled={pending || value.trim() === ''}>
          {pending ? <Spinner label={null} /> : '보내기'}
        </Button>
      </div>
    </div>
  )
}

function AnswerBlock({ answer }: { answer: AiAnswer }) {
  const sources = answer.sources ?? []
  const list = answer.list
  return (
    <div className="flex min-w-0 flex-col gap-3">
      <p className="text-body1-regular whitespace-pre-wrap text-text-primary">{answer.answer}</p>
      {list && list.rows.length > 0 && (
        <div className="flex flex-col rounded-lg bg-bg-surface px-4 inset-ring inset-ring-border-default">
          {list.rows.map((row) => (
            <div
              key={row.orderNo}
              className="grid grid-cols-[minmax(0,1fr)_64px_minmax(0,1.4fr)] items-center gap-3 border-b border-border-default py-3 text-caption-regular last:border-b-0"
            >
              <span className="text-text-secondary">{row.orderNo}</span>
              <span className="text-caption-medium text-text-primary">{row.name}</span>
              <span className="text-text-secondary">{row.detail}</span>
            </div>
          ))}
          {list.moreCount > 0 && (
            <p className="border-t border-border-default py-3 text-caption-regular text-text-secondary">
              외 {list.moreCount}건
            </p>
          )}
        </div>
      )}
      {sources.map((source) => {
        const line = sourceLine(source)
        return (
          <div key={source.screen} className="flex flex-col gap-1">
            {line && <p className="text-caption-regular text-text-tertiary">{line}</p>}
            {source.buttonLabel && safeInternalPath(source.path) && (
              <Link
                // 서버가 준 주소라 사이트 안 경로일 때만 링크로 만든다
                href={safeInternalPath(source.path)!}
                className="text-caption-medium text-text-brand hover:underline"
              >
                {source.buttonLabel} ›
              </Link>
            )}
          </div>
        )
      })}
    </div>
  )
}

// 피그마 판매자 · 관리자 크루런 AI. 첫 화면(로고 · 인사 · 입력칸 · 추천 질문)과 대화 화면을 한 부품이 그린다.
// 대화 기록은 서버가 화면별 · 회원별로 들고 있고(새 대화 = DELETE), 이 화면은 이번에 오간 대화만 보여 준다.
export function AiChat({
  screen,
  greeting,
  placeholder,
  scopeNote,
  suggestions,
  footnote,
}: AiChatProps) {
  const chat = useAiChat(screen)
  const clear = useAiClear(screen)
  const [turns, setTurns] = useState<Turn[]>([])
  const [draft, setDraft] = useState('')
  const nextId = useRef(1)
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView?.({ block: 'end' })
  }, [turns, chat.isPending])

  function push(turn: DistributiveOmit<Turn, 'id'>) {
    setTurns((prev) => [...prev, { ...turn, id: nextId.current++ } as Turn])
  }

  function send(text: string) {
    const message = text.trim()
    if (!message || chat.isPending) return
    push({ role: 'user', text: message })
    setDraft('')
    chat.mutate(message, {
      onSuccess: (answer) => push({ role: 'ai', answer }),
      onError: (error) => push({ role: 'error', text: aiErrorMessage(error) }),
    })
  }

  function startOver() {
    clear.mutate(undefined, {
      onSuccess: () => setTurns([]),
    })
  }

  if (turns.length === 0) {
    return (
      <main className="flex min-w-0 flex-1 flex-col items-center justify-center px-10 pb-16">
        <div className="flex w-full max-w-135 flex-col items-center gap-6">
          <Logo className="h-14 w-auto" />
          <h1 className="text-title2">{greeting}</h1>
          <div className="w-full">
            <Composer
              value={draft}
              onChange={setDraft}
              onSend={() => send(draft)}
              pending={chat.isPending}
              placeholder={placeholder}
              scopeNote={scopeNote}
              rows={2}
            />
          </div>
          <div className="flex flex-wrap justify-center gap-2">
            {suggestions.map((suggestion) => (
              <Chip key={suggestion} onClick={() => send(suggestion)}>
                {suggestion}
              </Chip>
            ))}
          </div>
        </div>
      </main>
    )
  }

  return (
    <main className="flex h-dvh min-w-0 flex-1 flex-col px-10 pt-10 pb-6">
      <div className="mx-auto flex w-full max-w-(--layout-content-max-width) items-center justify-between">
        <div className="flex items-center gap-2">
          <BrandMark className="size-8" />
          <h1 className="text-body1-bold">크루런 AI</h1>
        </div>
        <Button
          variant="text"
          size="s"
          className="text-text-brand"
          onClick={startOver}
          disabled={clear.isPending}
        >
          새 대화
        </Button>
      </div>
      {clear.isError && (
        <div className="mx-auto mt-4 w-full max-w-135">
          <Alert
            tone="danger"
            title="새 대화를 시작하지 못했어요"
            description={aiErrorMessage(clear.error)}
          />
        </div>
      )}
      <div className="mt-6 flex-1 overflow-y-auto">
        <div className="mx-auto flex w-full max-w-135 flex-col gap-6">
          {turns.map((turn) => {
            if (turn.role === 'user') {
              return (
                <p key={turn.id} className="self-end text-body1-regular text-text-primary">
                  {turn.text}
                </p>
              )
            }
            if (turn.role === 'error') {
              return (
                <Alert
                  key={turn.id}
                  tone="danger"
                  title="답을 받지 못했어요"
                  description={turn.text}
                />
              )
            }
            return (
              <div key={turn.id} className="flex items-start gap-3">
                <BrandMark className="size-7" />
                <AnswerBlock answer={turn.answer} />
              </div>
            )
          })}
          {chat.isPending && (
            <div className="flex items-center gap-3" role="status" aria-label="답을 만드는 중">
              <BrandMark className="size-7" />
              <Spinner label={null} className="text-text-tertiary" />
            </div>
          )}
          <div ref={bottomRef} />
        </div>
      </div>
      <div className="mx-auto mt-4 flex w-full max-w-135 flex-col items-center gap-2">
        <div className="w-full">
          <Composer
            value={draft}
            onChange={setDraft}
            onSend={() => send(draft)}
            pending={chat.isPending}
            placeholder="이어서 물어보세요"
            scopeNote={scopeNote}
            rows={1}
          />
        </div>
        <p className="text-caption-regular text-text-tertiary">{footnote}</p>
      </div>
    </main>
  )
}

type DistributiveOmit<T, K extends keyof T> = T extends unknown ? Omit<T, K> : never
