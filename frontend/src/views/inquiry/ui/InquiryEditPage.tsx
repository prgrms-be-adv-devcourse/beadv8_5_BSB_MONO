'use client'

import { useRouter } from 'next/navigation'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { LoadErrorBody } from '@/widgets/status-screens'
import {
  BODY_MAX,
  inquiryErrorMessage,
  TITLE_MAX,
  useEditInquiry,
  useMyInquiry,
  type Inquiry,
} from '@/entities/inquiry'
import { BottomCta, Button, Skeleton, Textarea, TextField, useToast } from '@/shared/ui'

// 피그마 문의 · M44 문의 고치기. 답변 전에만 된다(답변이 달렸으면 서버가 409로 막는다)
export function InquiryEditPage({ inquiryId }: { inquiryId: number }) {
  const inquiry = useMyInquiry(inquiryId)
  const header = <AppHeader type="sub" title="문의 고치기" />
  if (inquiry.isError) {
    return (
      <>
        {header}
        <LoadErrorBody onRetry={() => inquiry.refetch()} retrying={inquiry.isFetching} />
      </>
    )
  }
  if (!inquiry.data) {
    return (
      <>
        {header}
        <div role="status" aria-label="불러오는 중" className="p-4">
          <Skeleton shape="rect" className="h-40" />
        </div>
      </>
    )
  }
  return <EditForm inquiry={inquiry.data} header={header} />
}

function EditForm({ inquiry, header }: { inquiry: Inquiry; header: React.ReactNode }) {
  const router = useRouter()
  const toast = useToast()
  const edit = useEditInquiry(inquiry.id)
  const [title, setTitle] = useState(inquiry.title)
  const [body, setBody] = useState(inquiry.body)
  const ready = title.trim() !== '' && body.trim() !== ''

  const save = () =>
    edit.mutate(
      { title: title.trim(), body: body.trim() },
      {
        onSuccess: () => router.replace(`/me/inquiries/${inquiry.id}`),
        onError: (error) => toast({ message: inquiryErrorMessage(error), tone: 'error' }),
      },
    )

  return (
    <>
      {header}
      <main className="flex flex-1 flex-col gap-4 px-4 pt-4">
        <TextField
          label="제목"
          value={title}
          maxLength={TITLE_MAX}
          onChange={(e) => setTitle(e.target.value)}
          helper={`${title.length}/${TITLE_MAX}`}
        />
        <Textarea
          label="문의 내용"
          value={body}
          maxLength={BODY_MAX}
          showCounter
          onChange={(e) => setBody(e.target.value)}
        />
      </main>
      <BottomCta
        className="sticky bottom-0 z-10"
        primary={
          <Button size="l" disabled={!ready} loading={edit.isPending} onClick={save}>
            저장하기
          </Button>
        }
      />
    </>
  )
}
