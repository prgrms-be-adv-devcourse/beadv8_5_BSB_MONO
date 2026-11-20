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
import { BottomCta, Button, buttonClassName, Dialog, Skeleton, useToast } from '@/shared/ui'

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
        <p
          className={
            data.status === 'ANSWERED'
              ? 'text-body1-bold text-text-success'
              : 'text-caption-regular text-text-secondary'
          }
        >
          {status.label}
        </p>
        <h2 className="text-title3">{data.title}</h2>
        <p className="text-caption-regular text-text-secondary">
          <InquiryMeta inquiry={data} suffix={`${dotDateTime(data.createDate)} 보냄`} />
        </p>

        <section className="mt-4 flex flex-col gap-3">
          <h3 className="text-body1-bold">문의 내용</h3>
          <p className="rounded-md border border-border-default p-4 text-body1-regular whitespace-pre-wrap shadow-sm">
            {data.body}
          </p>
        </section>

        {data.status === 'ANSWERED' && data.answer && (
          <section className="mt-4 flex flex-col gap-3">
            <div className="flex items-center justify-between gap-2">
              <h3 className="text-body1-bold">
                {[
                  `${ASSIGNEE_ANSWERER[data.assignee]} 답변`,
                  data.assignee === 'SELLER' && sellerName,
                ]
                  .filter(Boolean)
                  .join(' · ')}
              </h3>
              {data.answeredAt && (
                <span className="shrink-0 text-caption-regular text-text-secondary">
                  {dotDateTime(data.answeredAt)} 답변
                </span>
              )}
            </div>
            <p className="rounded-md border border-border-default p-4 text-body1-regular whitespace-pre-wrap shadow-sm">
              {data.answer}
            </p>
          </section>
        )}
      </main>
      {data.status === 'WAITING' && (
        <BottomCta
          className="sticky bottom-0 z-10 border-t border-border-default"
          secondary={
            <Button
              variant="ghost"
              size="l"
              className="text-text-danger"
              onClick={() => setConfirmOpen(true)}
            >
              삭제하기
            </Button>
          }
          primary={
            <Link href={`/me/inquiries/${data.id}/edit`} className={buttonClassName({ size: 'l' })}>
              수정하기
            </Link>
          }
        />
      )}
      <Dialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        type="danger"
        title="문의를 삭제할까요?"
        body="삭제한 문의는 되돌릴 수 없어요."
        confirmLabel="삭제하기"
        confirming={remove.isPending}
        onConfirm={onDelete}
      />
    </>
  )
}
