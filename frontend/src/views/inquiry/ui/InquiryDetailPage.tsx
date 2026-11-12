'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { LoadErrorBody } from '@/widgets/status-screens'
import {
  ASSIGNEE_ANSWERER,
  dotDateTime,
  inquiryErrorMessage,
  STATUS,
  useDeleteInquiry,
  useMyInquiry,
  useRaceSellerName,
} from '@/entities/inquiry'
import { Badge, Button, buttonClassName, Dialog, Skeleton, useToast } from '@/shared/ui'

import { InquiryMeta } from './MyInquiriesPage'

// 피그마 문의 · M40 답변 전(배지 「답변 기다림」) · M40-1 답변 후 · M40-2 지우기 확인. 고치기 · 지우기는 답변 전에만 보인다
export function InquiryDetailPage({ inquiryId }: { inquiryId: number }) {
  const router = useRouter()
  const toast = useToast()
  const inquiry = useMyInquiry(inquiryId)
  const remove = useDeleteInquiry(inquiryId)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const data = inquiry.data
  const sellerName = useRaceSellerName(data?.raceId ?? null)

  const header = <AppHeader type="sub" title="문의 상세" backHref="/me/inquiries" />
  if (inquiry.isError) {
    return (
      <>
        {header}
        <LoadErrorBody onRetry={() => inquiry.refetch()} retrying={inquiry.isFetching} />
      </>
    )
  }
  if (!data) {
    return (
      <>
        {header}
        <div role="status" aria-label="불러오는 중" className="flex flex-col gap-3 p-4">
          <Skeleton shape="rect" className="h-6 w-20" />
          <Skeleton shape="rect" className="h-16" />
        </div>
      </>
    )
  }

  const status = STATUS[data.status]
  const onDelete = () =>
    remove.mutate(undefined, {
      onSuccess: () => router.replace('/me/inquiries?deleted=1'),
      onError: (error) => {
        setConfirmOpen(false)
        toast({ message: inquiryErrorMessage(error), tone: 'error' })
      },
    })

  return (
    <>
      {header}
      <main className="flex flex-1 flex-col gap-3 px-4 pt-4 pb-6">
        <Badge tone={status.tone} className="self-start">
          {data.status === 'WAITING' ? '답변 기다림' : status.label}
        </Badge>
        <h2 className="text-title2">{data.title}</h2>
        <p className="text-caption-regular text-text-tertiary">
          <InquiryMeta inquiry={data} suffix={`${dotDateTime(data.createDate)} 보냄`} />
        </p>
        <p className="text-body2-regular whitespace-pre-wrap text-text-secondary">{data.body}</p>

        {data.status === 'WAITING' ? (
          <div className="flex gap-2">
            <Link
              href={`/me/inquiries/${data.id}/edit`}
              className={buttonClassName({ variant: 'ghost', size: 's' })}
            >
              고치기
            </Link>
            <Button variant="ghost" size="s" onClick={() => setConfirmOpen(true)}>
              지우기
            </Button>
          </div>
        ) : (
          data.answer && (
            <section className="mt-3 flex flex-col gap-2 rounded-lg bg-bg-brand-subtle p-4">
              <h3 className="text-body2-medium text-text-brand">
                {[
                  `${ASSIGNEE_ANSWERER[data.assignee]} 답변`,
                  data.assignee === 'SELLER' && sellerName,
                ]
                  .filter(Boolean)
                  .join(' · ')}
              </h3>
              <p className="text-body2-regular whitespace-pre-wrap text-text-secondary">
                {data.answer}
              </p>
              {data.answeredAt && (
                <p className="text-caption-regular text-text-tertiary">
                  {dotDateTime(data.answeredAt)} 답변
                </p>
              )}
            </section>
          )
        )}
      </main>
      <Dialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        type="danger"
        title="문의를 지울까요?"
        body="지운 문의는 되돌릴 수 없어요."
        confirmLabel="지우기"
        confirming={remove.isPending}
        onConfirm={onDelete}
      />
    </>
  )
}
