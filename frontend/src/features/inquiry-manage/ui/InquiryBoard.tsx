'use client'

import Image from 'next/image'
import { useRouter } from 'next/navigation'
import { useState, type ReactNode } from 'react'

import {
  answerSchema,
  inquiryErrorMessage,
  slashDate,
  slashDateTime,
  TYPE_LABEL,
  useAnswerInquiry,
  useManageInquiries,
  useManageInquiry,
  useMyInquiries,
  useMyInquiry,
  useRaceName,
  type Inquiry,
  type InquiryOrder,
  type ManageFilters,
  type ManageRole,
} from '@/entities/inquiry'
import { cn, useSearchParamsState } from '@/shared/lib'
import { Button, Chip, EmptyState, FOCUS_RING, KeyValue, Skeleton, useToast } from '@/shared/ui'

type Filter = 'WAITING' | 'ANSWERED' | 'SELLER' | 'SENT'

// 주문 서비스가 정한 값은 한글로 바꾸고, 모르는 값은 그대로 보여 준다
const DELIVERY: Record<string, string> = {
  PREPARING: '제작 전',
  SHIPPING: '배송 중',
  DELIVERED: '배송 완료',
}
const ORDER_STATUS: Record<string, string> = { PAID: '참가 확정', CANCELED: '취소됨' }

const won = (n: number) => `₩${n.toLocaleString('ko-KR')}`

/** 답변칸. 판매자 · 관리자 화면이 AI 초안 부품을 끼워 넣는다(같은 층 슬라이스끼리는 서로 가져오지 못해서) */
export type RenderAnswer = (args: {
  inquiryId: number
  onSend: (text: string) => void
  sending: boolean
}) => ReactNode

/** 「새벽토끼 · 춘천 호수 하프마라톤」. 대회가 없는 문의(판매자 문의 · 참가권 없는 문의)는 이름만 */
function Who({ inquiry }: { inquiry: Inquiry }) {
  const raceName = useRaceName(inquiry.raceId)
  const who =
    inquiry.memberNickname ?? (inquiry.type === 'SELLER_SUPPORT' ? '판매자' : '알 수 없음')
  return <>{[who, raceName].filter(Boolean).join(' · ')}</>
}

// 피그마 S13 · A13 문의 (판매자 · 관리자 공통): 왼쪽에 문의 줄, 오른쪽에 고른 문의와 답변
// 탭은 「답변 대기 N · 답변 완료」, 관리자는 「판매자 담당」이 더해진다(판매자가 답할 문의를 볼 수만 있다)
// 판매자는 「보낸 문의」가 더해진다. 크루런에 보낸 문의는 판매자 목록 API에 없어 내 문의 API로 받는다
// initialId: 알림 링크(/seller/inquiries/12)로 들어왔을 때 처음 고를 문의
export function InquiryBoard({
  role,
  initialId,
  renderAnswer,
}: {
  role: ManageRole
  initialId?: number
  renderAnswer: RenderAnswer
}) {
  const router = useRouter()
  // 거르기는 URL(?filter=)에 담는다
  const [url, setUrl] = useSearchParamsState(
    { filter: 'WAITING' },
    // 역할에 없는 탭(관리자의 SENT, 판매자의 SELLER)은 주소에 있어도 답변 대기로 읽는다
    {
      filter:
        role === 'seller' ? ['WAITING', 'ANSWERED', 'SENT'] : ['WAITING', 'ANSWERED', 'SELLER'],
    },
  )
  const filter = url.filter as Filter
  const setFilter = (next: Filter) => setUrl({ filter: next })
  const sent = role === 'seller' && filter === 'SENT'
  const [picked, setPicked] = useState<number | null>(initialId ?? null)
  const filters: ManageFilters = {
    status: filter === 'WAITING' || filter === 'ANSWERED' ? filter : undefined,
    assignee: filter === 'SELLER' ? 'SELLER' : undefined,
  }
  // 보낸 문의 탭에서도 「답변 대기 N」 개수를 보여 주려고 판매자 목록은 계속 받는다
  const managed = useManageInquiries(role, filters)
  const mine = useMyInquiries(sent)
  const list = sent ? mine : managed
  // 구매자로 남긴 문의는 판매자 콘솔에 섞지 않는다
  // shortcut: 내 문의 첫 20개 안에서만 보인다, 판매자 문의가 쌓이면 더 보기나 판매자 문의만 주는 API로 바꾼다
  const items = sent
    ? mine.data?.inquiries.filter((i) => i.type === 'SELLER_SUPPORT')
    : managed.data?.inquiries
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
    <div className="flex flex-1 flex-col gap-6">
      <div className="flex gap-2">
        <Chip selected={filter === 'WAITING'} onClick={() => setFilter('WAITING')}>
          답변 대기{managed.data ? ` ${managed.data.waitingCount}` : ''}
        </Chip>
        <Chip selected={filter === 'ANSWERED'} onClick={() => setFilter('ANSWERED')}>
          답변 완료
        </Chip>
        {role === 'admin' && (
          <Chip selected={filter === 'SELLER'} onClick={() => setFilter('SELLER')}>
            판매자 담당
          </Chip>
        )}
        {role === 'seller' && (
          <Chip selected={sent} onClick={() => setFilter('SENT')}>
            보낸 문의
          </Chip>
        )}
      </div>
      {items?.length === 0 ? (
        <div className="flex flex-1 items-center justify-center">
          <EmptyState
            illustration={<Image src="/brand/mascot-default.svg" alt="" width={96} height={96} />}
            title={
              sent
                ? '보낸 문의가 없어요'
                : filter === 'WAITING'
                  ? '답변을 기다리는 문의가 없어요'
                  : '문의가 없어요'
            }
          />
        </div>
      ) : (
        <div className="flex items-start gap-6">
          <section
            aria-label="문의 목록"
            className="w-90 shrink-0 rounded-md bg-bg-surface p-2 inset-ring inset-ring-border-default"
          >
            {items === undefined ? (
              <div role="status" aria-label="불러오는 중" className="flex flex-col gap-2">
                {Array.from({ length: 4 }, (_, i) => (
                  <Skeleton key={i} shape="rect" className="h-24" />
                ))}
              </div>
            ) : (
              <ul className="flex flex-col">
                {items.map((inquiry) => (
                  <li key={inquiry.id}>
                    <button
                      type="button"
                      aria-pressed={inquiry.id === selectedId}
                      onClick={() => {
                        setPicked(inquiry.id)
                        // 거르기 탭(?filter=)이 상세 주소로 옮겨도 남게 한다
                        router.replace(
                          `/${role}/inquiries/${inquiry.id}${filter === 'WAITING' ? '' : `?filter=${filter}`}`,
                        )
                      }}
                      className={cn(
                        'flex w-full flex-col items-start gap-1 rounded-lg p-3 text-left',
                        inquiry.id === selectedId && 'bg-bg-subtle',
                        FOCUS_RING,
                      )}
                    >
                      <span className="flex w-full items-center gap-2">
                        <span className="min-w-0 flex-1 truncate text-caption-regular text-text-tertiary">
                          <Who inquiry={inquiry} />
                        </span>
                        <span className="shrink-0 text-caption-regular text-text-tertiary">
                          {slashDate(inquiry.createDate)}
                        </span>
                      </span>
                      <span className="w-full truncate text-body1-medium">{inquiry.title}</span>
                      <span className="text-caption-regular text-text-tertiary">
                        {TYPE_LABEL[inquiry.type]}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>
          <section aria-label="문의 상세" className="min-w-0 flex-1">
            {selectedId !== null && (
              <Detail
                key={selectedId}
                role={role}
                id={selectedId}
                sent={sent}
                renderAnswer={renderAnswer}
                onSent={() => {
                  // 답변을 보내면 답변 완료 목록으로 옮겨 가고, 방금 답한 문의가 그대로 열려 있다
                  setPicked(selectedId)
                  setFilter('ANSWERED')
                }}
              />
            )}
          </section>
        </div>
      )}
    </div>
  )
}

function Detail({
  role,
  id,
  sent,
  renderAnswer,
  onSent,
}: {
  role: ManageRole
  id: number
  /** 판매자가 크루런에 보낸 문의. 내 문의 API로 받고 답변칸이 없다 */
  sent: boolean
  renderAnswer: RenderAnswer
  onSent: () => void
}) {
  const managed = useManageInquiry(role, sent ? null : id)
  const mine = useMyInquiry(id, sent)
  const detail = sent ? mine : managed
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
  const answerable = !sent && (role === 'seller' || data.assignee === 'ADMIN')
  const badge =
    data.status === 'ANSWERED'
      ? { label: '답변 완료' }
      : { label: role === 'admin' && !answerable ? '판매자 답변 대기' : '답변 대기' }

  return (
    <article className="flex flex-col gap-6 rounded-md bg-bg-surface p-6 inset-ring inset-ring-border-default">
      <header className="flex flex-col gap-2">
        <span
          className={cn(
            'text-caption-regular',
            data.status === 'ANSWERED' ? 'text-text-success' : 'text-text-tertiary',
          )}
        >
          {badge.label}
        </span>
        <h2 className="text-title3">{data.title}</h2>
        <p className="text-caption-regular text-text-tertiary">{meta}</p>
        <p className="pt-4 text-body1-regular whitespace-pre-wrap">{data.body}</p>
      </header>
      {data.order && <OrderInfo order={data.order} />}
      {data.status === 'ANSWERED' ? (
        <section className="flex flex-col gap-2">
          <div className="flex items-baseline gap-2">
            <h3 className="text-body1-bold">답변</h3>
            {data.answeredAt && (
              <span className="text-caption-regular text-text-tertiary">
                {slashDateTime(data.answeredAt)}에 보냈어요
              </span>
            )}
          </div>
          <p className="text-body1-regular whitespace-pre-wrap">{data.answer}</p>
        </section>
      ) : (
        answerable && (
          <AnswerSender role={role} id={id} onSent={onSent} renderAnswer={renderAnswer} />
        )
      )}
    </article>
  )
}

function OrderInfo({ order }: { order: InquiryOrder }) {
  const item = order.items[0]
  const canceled = order.status === 'CANCELED'
  return (
    <section className="flex flex-col gap-4 rounded-md p-4 shadow-sm inset-ring inset-ring-border-default">
      <h3 className="sr-only">주문 정보</h3>
      <KeyValue label="주문번호" value={order.orderNo} />
      {item && <KeyValue label="대회 · 코스" value={`${item.raceName} · ${item.courseName}`} />}
      {item && <KeyValue label="키트" value={item.kitSummary ?? '고르지 않음'} />}
      {item && (
        <KeyValue
          label="배송 상태"
          value={
            item.deliveryStatus
              ? (DELIVERY[item.deliveryStatus] ?? item.deliveryStatus)
              : '배송할 키트 없음'
          }
        />
      )}
      {order.paymentAmount !== null && (
        <KeyValue label="결제 금액" value={`${won(order.paymentAmount)} · 예치금 결제`} />
      )}
      <KeyValue
        label="주문 상태"
        value={
          canceled ? (
            <span className="text-text-danger">{ORDER_STATUS[order.status]}</span>
          ) : (
            (ORDER_STATUS[order.status] ?? order.status)
          )
        }
      />
    </section>
  )
}

function AnswerSender({
  role,
  id,
  onSent,
  renderAnswer,
}: {
  role: ManageRole
  id: number
  onSent: () => void
  renderAnswer: RenderAnswer
}) {
  const toast = useToast()
  const answer = useAnswerInquiry(role, id)

  const send = (text: string) => {
    const parsed = answerSchema.safeParse(text)
    if (!parsed.success) return
    answer.mutate(parsed.data, {
      onSuccess: () => {
        toast({ message: '답변을 보냈어요. 답변 완료 목록으로 옮겼어요.' })
        onSent()
      },
      onError: (error) => toast({ message: inquiryErrorMessage(error), tone: 'error' }),
    })
  }

  return <>{renderAnswer({ inquiryId: id, onSend: send, sending: answer.isPending })}</>
}
