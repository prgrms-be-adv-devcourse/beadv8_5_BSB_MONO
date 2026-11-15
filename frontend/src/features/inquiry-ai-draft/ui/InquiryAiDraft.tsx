'use client'

import { useEffect, useRef, useState } from 'react'

import {
  aiErrorMessage,
  useInquiryDraft,
  type AiScreen,
  type InquiryDraft,
} from '@/entities/ai-assistant'
import { ANSWER_MAX } from '@/entities/inquiry'
import { Alert, Badge, Button, Checkbox, Spinner } from '@/shared/ui'

/** 초안이 모르는 정보를 비워 둔 자리 (예: [확인 필요: 발송 예정일]) */
const UNKNOWN_MARK = /\[확인 필요: [^\]]+\]/g

export type InquiryAiDraftProps = {
  screen: AiScreen
  inquiryId: number | string
  /** 답변 보내기. 실제 전송 API는 문의 화면이 쥐고 있다 */
  onSend: (text: string) => void | Promise<void>
  sending?: boolean
}

// 피그마 문의 답변 · AI 초안 (판매자 1388:65152 · 1388:65206, 관리자 1388:68471 · 1388:68525).
// 문의를 열면 답변칸에 초안이 먼저 들어온다. 비워 둔 자리가 남아 있거나 확인 체크가 없으면 보내지 못한다.
// 초안을 만들지 못하면 안내를 띄우고 직접 쓰거나 「다시 만들기」를 누른다(이때는 확인 체크 없이 보낼 수 있다).
export function InquiryAiDraft({
  screen,
  inquiryId,
  onSend,
  sending = false,
}: InquiryAiDraftProps) {
  const request = useInquiryDraft(screen)
  const [draft, setDraft] = useState<InquiryDraft | null>(null)
  const [text, setText] = useState('')
  const [confirmed, setConfirmed] = useState(false)
  const started = useRef(false)

  function fetchDraft() {
    request.mutate(inquiryId, {
      onSuccess: (result) => {
        setDraft(result)
        setText(result.draft)
        setConfirmed(false)
      },
    })
  }

  useEffect(() => {
    if (started.current) return
    started.current = true
    fetchDraft()
    // 문의를 열 때 한 번만 받는다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const firstLoading = request.isPending && !draft
  const unknownCount = text.match(UNKNOWN_MARK)?.length ?? 0
  const canSend = text.trim() !== '' && !sending && (!draft || (confirmed && unknownCount === 0))

  return (
    <div className="flex flex-col gap-3">
      {request.isError && (
        <Alert
          tone="danger"
          title={draft ? '초안을 다시 만들지 못했어요' : '답변 초안을 만들지 못했어요'}
          description={`${aiErrorMessage(request.error)} 직접 쓰거나 다시 만들기를 눌러 주세요.`}
        />
      )}
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <span className="text-body1-bold">답변</span>
          {draft && unknownCount > 0 ? (
            <Badge tone="warning">확인 필요 {unknownCount}곳</Badge>
          ) : firstLoading ? (
            <Spinner label="초안을 만드는 중" className="size-4" />
          ) : draft ? (
            <span className="text-caption-regular text-text-secondary">
              AI 초안 · 보내기 전에 확인해 주세요
            </span>
          ) : null}
        </div>
        {!firstLoading && (
          <Button variant="ghost" size="s" onClick={fetchDraft} disabled={request.isPending}>
            {request.isPending ? <Spinner label={null} /> : '다시 만들기'}
          </Button>
        )}
      </div>
      <textarea
        aria-label="답변"
        value={text}
        maxLength={ANSWER_MAX}
        disabled={firstLoading}
        onChange={(event) => {
          setText(event.target.value)
          setConfirmed(false)
        }}
        className="h-36 w-full resize-none rounded-md p-4 text-body2-regular text-text-primary inset-ring inset-ring-border-default outline-none focus-visible:inset-ring-border-focus disabled:bg-bg-subtle"
      />
      {draft && draft.references.length > 0 && (
        <p className="text-caption-regular text-text-tertiary">
          참고한 정보 · {draft.references.join(', ')}
        </p>
      )}
      {draft &&
        unknownCount > 0 &&
        draft.missing.map((item) => (
          <div
            key={item.title}
            className="flex flex-col gap-1 rounded-md p-4 inset-ring inset-ring-border-default"
          >
            <span className="text-body2-bold">{item.title}</span>
            <span className="text-caption-regular text-text-secondary">
              {item.reason} 직접 적거나 그 문장을 지워 주세요.
            </span>
          </div>
        ))}
      <div className="flex items-center justify-between gap-3">
        {draft ? (
          <Checkbox
            label="초안 내용을 확인했어요"
            checked={confirmed}
            onCheckedChange={(checked) => setConfirmed(checked)}
          />
        ) : (
          <span className="text-body2-regular text-text-tertiary">보낸 답변은 고칠 수 없어요</span>
        )}
        <Button onClick={() => onSend(text)} disabled={!canSend}>
          답변 보내기
        </Button>
      </div>
    </div>
  )
}
