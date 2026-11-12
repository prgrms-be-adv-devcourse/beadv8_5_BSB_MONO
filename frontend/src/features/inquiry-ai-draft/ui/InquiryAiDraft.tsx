'use client'

import { useState } from 'react'

import {
  aiErrorMessage,
  useInquiryDraft,
  type AiScreen,
  type InquiryDraft,
} from '@/entities/ai-assistant'
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

// 피그마 문의 답변 · AI 초안 (판매자 1388:65152 · 1388:65206, 관리자 1388:68471).
// 「AI 초안 받기 → 초안 표시 → 확인 필요 표시」. 비워 둔 자리가 남아 있거나 확인 체크가 없으면 보내지 못한다.
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

  function fetchDraft() {
    request.mutate(inquiryId, {
      onSuccess: (result) => {
        setDraft(result)
        setText(result.draft)
        setConfirmed(false)
      },
    })
  }

  if (!draft) {
    return (
      <div className="flex flex-col gap-3">
        {request.isError && (
          <Alert
            tone="danger"
            title="초안을 만들지 못했어요"
            description={aiErrorMessage(request.error)}
          />
        )}
        <div>
          <Button variant="secondary" size="s" onClick={fetchDraft} disabled={request.isPending}>
            {request.isPending ? <Spinner label={null} /> : 'AI 초안 받기'}
          </Button>
        </div>
      </div>
    )
  }

  const unknownCount = text.match(UNKNOWN_MARK)?.length ?? 0
  const canSend = confirmed && unknownCount === 0 && text.trim() !== '' && !sending

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <span className="text-body2-bold">답변</span>
          {unknownCount > 0 ? (
            <Badge tone="warning">확인 필요 {unknownCount}곳</Badge>
          ) : (
            <span className="text-caption-regular text-text-secondary">
              AI 초안 · 보내기 전에 확인해 주세요
            </span>
          )}
        </div>
        <Button variant="ghost" size="s" onClick={fetchDraft} disabled={request.isPending}>
          다시 만들기
        </Button>
      </div>
      <textarea
        aria-label="답변"
        value={text}
        onChange={(event) => {
          setText(event.target.value)
          setConfirmed(false)
        }}
        className="h-30 w-full resize-none rounded-md p-4 text-body2-regular text-text-primary inset-ring inset-ring-border-default outline-none focus-visible:inset-ring-border-focus"
      />
      {request.isError && (
        <Alert
          tone="danger"
          title="초안을 다시 만들지 못했어요"
          description={aiErrorMessage(request.error)}
        />
      )}
      {unknownCount > 0 &&
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
      {draft.references.length > 0 && (
        <p className="text-caption-regular text-text-tertiary">
          참고한 정보 · {draft.references.join(', ')}
        </p>
      )}
      <div className="flex items-center justify-between gap-3">
        <Checkbox
          label="초안 내용을 확인했어요"
          checked={confirmed}
          onCheckedChange={(checked) => setConfirmed(checked)}
        />
        <Button onClick={() => onSend(text)} disabled={!canSend}>
          답변 보내기
        </Button>
      </div>
    </div>
  )
}
