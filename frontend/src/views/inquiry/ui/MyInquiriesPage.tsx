'use client'

import Link from 'next/link'
import { useEffect } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { LoadErrorBody } from '@/widgets/status-screens'
import {
  dotDate,
  STATUS,
  TYPE_LABEL,
  useMyInquiries,
  useRaceName,
  type Inquiry,
  type InquiryStatus,
} from '@/entities/inquiry'
import { cn, useSearchParamsState } from '@/shared/lib'
import {
  Badge,
  Button,
  Chip,
  Divider,
  EmptyState,
  FOCUS_RING,
  Skeleton,
  useToast,
} from '@/shared/ui'

type Filter = 'ALL' | InquiryStatus
const FILTERS: { value: Filter; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'WAITING', label: STATUS.WAITING.label },
  { value: 'ANSWERED', label: STATUS.ANSWERED.label },
]

/** 「춘천 호수 하프마라톤 · 대회 · 현장 · 11.10」. 대회가 없는 문의는 이름을 뺀다 */
export function InquiryMeta({ inquiry, suffix }: { inquiry: Inquiry; suffix?: string }) {
  const raceName = useRaceName(inquiry.raceId)
  const text = [raceName, TYPE_LABEL[inquiry.type], suffix ?? dotDate(inquiry.createDate)]
    .filter(Boolean)
    .join(' · ')
  return <>{text}</>
}

// 피그마 문의 · M39 내 문의 (전체 · 답변 대기만 · 비어 있음 · 보낸 직후 · 지운 뒤)
// notice는 문의를 보내거나 지운 직후 목록으로 올 때 한 번 띄우는 토스트 문구
export function MyInquiriesPage({ notice }: { notice?: string }) {
  // 거르기는 URL(?filter=)에 담는다
  const [url, setUrl] = useSearchParamsState(
    { filter: 'ALL' },
    { filter: FILTERS.map((f) => f.value) },
  )
  const filter = url.filter as Filter
  const inquiries = useMyInquiries()
  const toast = useToast()

  useEffect(() => {
    if (notice) toast({ message: notice })
    // 토스트는 처음 한 번만 띄운다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (inquiries.isError) {
    return (
      <>
        <AppHeader type="sub" title="내 문의" backHref="/me" />
        <LoadErrorBody onRetry={() => inquiries.refetch()} retrying={inquiries.isFetching} />
      </>
    )
  }

  const all = inquiries.data?.inquiries
  const shown = all?.filter((i) => filter === 'ALL' || i.status === filter)

  return (
    <>
      <AppHeader type="sub" title="내 문의" backHref="/me" />
      <main className="flex flex-1 flex-col">
        <div className="flex gap-2 px-4 py-4">
          {FILTERS.map((f) => (
            <Chip
              key={f.value}
              selected={filter === f.value}
              onClick={() => setUrl({ filter: f.value })}
            >
              {f.label}
            </Chip>
          ))}
        </div>
        {shown === undefined ? (
          <div role="status" aria-label="불러오는 중" className="flex flex-col gap-4 px-4">
            {Array.from({ length: 3 }, (_, i) => (
              <Skeleton key={i} shape="rect" className="h-14" />
            ))}
          </div>
        ) : shown.length === 0 ? (
          <EmptyState title="보낸 문의가 없어요" />
        ) : (
          <ul>
            {shown.map((inquiry) => (
              <li key={inquiry.id}>
                <Link
                  href={`/me/inquiries/${inquiry.id}`}
                  className={cn('flex items-center gap-3 px-4 py-4', FOCUS_RING)}
                >
                  <span className="flex min-w-0 flex-1 flex-col gap-1">
                    <span className="truncate text-body1-bold">{inquiry.title}</span>
                    <span className="truncate text-caption-regular text-text-tertiary">
                      <InquiryMeta inquiry={inquiry} />
                    </span>
                  </span>
                  <Badge tone={STATUS[inquiry.status].tone}>{STATUS[inquiry.status].label}</Badge>
                </Link>
                <Divider />
              </li>
            ))}
          </ul>
        )}
        {inquiries.hasNextPage && (
          <Button
            variant="secondary"
            className="mx-4 my-4"
            loading={inquiries.isFetchingNextPage}
            onClick={() => inquiries.fetchNextPage()}
          >
            더 보기
          </Button>
        )}
      </main>
    </>
  )
}
