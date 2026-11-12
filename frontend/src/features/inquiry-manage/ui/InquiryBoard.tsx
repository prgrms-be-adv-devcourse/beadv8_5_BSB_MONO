'use client'

import { useRouter } from 'next/navigation'
import { useState } from 'react'

import {
  ANSWER_MAX,
  inquiryErrorMessage,
  slashDate,
  slashDateTime,
  STATUS,
  TYPE_LABEL,
  useAnswerInquiry,
  useManageInquiries,
  useManageInquiry,
  useRaceName,
  type Inquiry,
  type InquiryOrder,
  type ManageFilters,
  type ManageRole,
} from '@/entities/inquiry'
import { cn } from '@/shared/lib'
import {
  Badge,
  Button,
  Chip,
  EmptyState,
  FOCUS_RING,
  KeyValue,
  Skeleton,
  Textarea,
  useToast,
} from '@/shared/ui'

type Filter = 'ALL' | 'WAITING' | 'SELLER'

// 주문 서비스가 정한 값은 한글로 바꾸고, 모르는 값은 그대로 보여 준다
const DELIVERY: Record<string, string> = {
  PREPARING: '배송 준비',
  SHIPPING: '배송 중',
  DELIVERED: '배송 완료',
}
const ORDER_STATUS: Record<string, string> = { PAID: '결제 완료', CANCELED: '취소' }

const won = (n: number) => `${n.toLocaleString('ko-KR')}원`

/** 「새벽토끼 · 춘천 호수 하프마라톤」. 대회가 없는 문의(판매자 문의 · 참가권 없는 문의)는 이름만 */
function Who({ inquiry }: { inquiry: Inquiry }) {
  const raceName = useRaceName(inquiry.raceId)
  const who =
    inquiry.memberNickname ?? (inquiry.type === 'SELLER_SUPPORT' ? '판매자' : '알 수 없음')
  return <>{[who, raceName].filter(Boolean).join(' · ')}</>
}

// 피그마 S13 · A13 문의 (판매자 · 관리자 공통): 왼쪽에 문의 줄, 오른쪽에 고른 문의와 답변
// 관리자는 「판매자 담당」 칩으로 판매자가 답할 문의를 볼 수만 있다(답변은 판매자 몫)
// initialId: 알림 링크(/seller/inquiries/12)로 들어왔을 때 처음 고를 문의
export function InquiryBoard({ role, initialId }: { role: ManageRole; initialId?: number }) {
  const router = useRouter()
  const [filter, setFilter] = useState<Filter>('ALL')
  const [picked, setPicked] = useState<number | null>(initialId ?? null)
  const filters: ManageFilters = {
    status: filter === 'WAITING' ? 'WAITING' : undefined,
    assignee: filter === 'SELLER' ? 'SELLER' : undefined,
  }
  const list = useManageInquiries(role, filters)
  const items = list.data?.inquiries
  const selectedId =
    picked !== null && items?.some((i) => i.id === picked) ? picked : (items?.[0]?.id ?? null)

  if (list.isError) {
    return (
      <EmptyState
        role="alert"
        title="문의를 불러오지 못했어요"
        action={
          <Button variant="secondary" onClick={() => list.refetch()} loading={list.isFetching}>
            다시 시도하기
          </Button>
        }
      />
    )
  }

  return (
    <div className="flex items-start gap-6">
      <section aria-label="문의 목록" className="flex w-96 shrink-0 flex-col gap-3">
        <div className="flex gap-2">
          <Chip selected={filter === 'ALL'} onClick={() => setFilter('ALL')}>
            전체
          </Chip>
          <Chip selected={filter === 'WAITING'} onClick={() => setFilter('WAITING')}>
            답변 대기{list.data ? ` ${list.data.waitingCount}` : ''}
          </Chip>
          {role === 'admin' && (
            <Chip selected={filter === 'SELLER'} onClick={() => setFilter('SELLER')}>
              판매자 담당
            </Chip>
          )}
        </div>
        {items === undefined ? (
          <div role="status" aria-label="불러오는 중" className="flex flex-col gap-2">
            {Array.from({ length: 4 }, (_, i) => (
              <Skeleton key={i} shape="rect" className="h-16" />
            ))}
          </div>
        ) : items.length === 0 ? (
          <EmptyState title="문의가 없어요" />
        ) : (
          <ul className="flex flex-col gap-2">
            {items.map((inquiry) => (
              <li key={inquiry.id}>
                <button
                  type="button"
                  aria-pressed={inquiry.id === selectedId}
                  onClick={() => {
                    setPicked(inquiry.id)
                    router.replace(`/${role}/inquiries/${inquiry.id}`)
                  }}
                  className={cn(
                    'flex w-full items-start gap-3 rounded-lg p-4 text-left inset-ring',
                    inquiry.id === selectedId
                      ? 'bg-bg-brand-subtle inset-ring-2 inset-ring-border-brand'
                      : 'bg-bg-surface inset-ring-border-default',
                    FOCUS_RING,
                  )}
                >
                  <span className="flex min-w-0 flex-1 flex-col gap-1">
                    <span className="truncate text-caption-regular text-text-tertiary">
                      <Who inquiry={inquiry} />
                    </span>
                    <span className="truncate text-body1-bold">{inquiry.title}</span>
                    <span className="text-caption-regular text-text-tertiary">
                      {slashDate(inquiry.createDate)}
                    </span>
                  </span>
                  <Badge tone={STATUS[inquiry.status].tone}>{STATUS[inquiry.status].label}</Badge>
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
      <section aria-label="문의 상세" className="min-w-0 flex-1">
        {selectedId !== null && <Detail key={selectedId} role={role} id={selectedId} />}
      </section>
    </div>
  )
}

function Detail({ role, id }: { role: ManageRole; id: number }) {
  const detail = useManageInquiry(role, id)
  const data = detail.data
  const raceName = useRaceName(data?.raceId ?? null)

  if (detail.isError) {
    return (
      <EmptyState
        role="alert"
        title="문의를 불러오지 못했어요"
        action={
          <Button variant="secondary" onClick={() => detail.refetch()} loading={detail.isFetching}>
            다시 시도하기
          </Button>
        }
      />
    )
  }
  if (!data) return <Skeleton shape="rect" className="h-64" />

  const meta = [
    data.memberNickname ?? '판매자',
    raceName,
    TYPE_LABEL[data.type],
    slashDateTime(data.createDate),
  ]
    .filter(Boolean)
    .join(' · ')
  // 관리자는 관리자 담당 문의에만 답한다. 판매자 담당 문의는 서버가 404로 막는다
  const answerable = role === 'seller' || data.assignee === 'ADMIN'

  return (
    <article className="flex flex-col gap-6 rounded-lg bg-bg-surface p-6 inset-ring inset-ring-border-default">
      <header className="flex flex-col gap-2">
        <div className="flex items-center gap-2">
          <h2 className="min-w-0 flex-1 text-title2">{data.title}</h2>
          <Badge tone={STATUS[data.status].tone}>{STATUS[data.status].label}</Badge>
        </div>
        <p className="text-caption-regular text-text-tertiary">{meta}</p>
        <p className="text-body2-regular whitespace-pre-wrap">{data.body}</p>
      </header>
      {data.order && <OrderInfo order={data.order} />}
      <section className="flex flex-col gap-3">
        <h3 className="text-body1-bold">답변</h3>
        {data.status === 'ANSWERED' ? (
          <>
            {data.answeredAt && (
              <p className="text-caption-regular text-text-tertiary">
                {slashDateTime(data.answeredAt)}에 보냈어요
              </p>
            )}
            <p className="text-body2-regular whitespace-pre-wrap">{data.answer}</p>
          </>
        ) : (
          answerable && <AnswerForm role={role} id={id} />
        )}
      </section>
    </article>
  )
}

function OrderInfo({ order }: { order: InquiryOrder }) {
  const item = order.items[0]
  const canceled = order.status === 'CANCELED'
  return (
    <section className="flex flex-col gap-2">
      <h3 className="text-body1-bold">주문 정보</h3>
      <KeyValue label="주문번호" value={order.orderNo} />
      {item && <KeyValue label="코스" value={item.courseName} />}
      {item && <KeyValue label="키트" value={item.kitSummary ?? '-'} />}
      {item && (
        <KeyValue
          label="배송 상태"
          value={item.deliveryStatus ? (DELIVERY[item.deliveryStatus] ?? item.deliveryStatus) : '-'}
        />
      )}
      {order.paymentAmount !== null && (
        <KeyValue label="결제 금액" value={won(order.paymentAmount)} />
      )}
      <KeyValue
        label="주문 상태"
        value={
          canceled ? (
            <Badge tone="danger">{ORDER_STATUS[order.status]}</Badge>
          ) : (
            (ORDER_STATUS[order.status] ?? order.status)
          )
        }
      />
    </section>
  )
}

function AnswerForm({ role, id }: { role: ManageRole; id: number }) {
  const toast = useToast()
  const answer = useAnswerInquiry(role, id)
  const [content, setContent] = useState('')

  const send = () =>
    answer.mutate(content.trim(), {
      onSuccess: () => toast({ message: '답변을 보냈어요' }),
      onError: (error) => toast({ message: inquiryErrorMessage(error), tone: 'error' }),
    })

  return (
    <>
      <Textarea
        aria-label="답변 내용"
        value={content}
        maxLength={ANSWER_MAX}
        showCounter
        onChange={(e) => setContent(e.target.value)}
      />
      <div className="flex items-center justify-end gap-3">
        <Badge tone="warning">되돌릴 수 없음</Badge>
        <Button disabled={content.trim() === ''} loading={answer.isPending} onClick={send}>
          답변 보내기
        </Button>
      </div>
    </>
  )
}
