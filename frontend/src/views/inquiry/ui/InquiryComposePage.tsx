'use client'

import { useQuery } from '@tanstack/react-query'
import { useRouter } from 'next/navigation'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import {
  ANSWERER_NOTE,
  ASSIGNEE_ANSWERER,
  ASSIGNEE_OF,
  BODY_MAX,
  inquiryErrorMessage,
  TITLE_MAX,
  TYPE_CHOICES,
  TYPE_LABEL,
  useCreateInquiry,
  useRaceDate,
  useRaceName,
  type InquiryType,
} from '@/entities/inquiry'
import { formatRaceDate } from '@/entities/race'
import { myTicketsQueryOptions, type MyTicket } from '@/entities/ticket'
import { cn, useNow } from '@/shared/lib'
import { BottomCta, Button, FOCUS_RING, Skeleton, Textarea, TextField, useToast } from '@/shared/ui'

const CARD = 'flex w-full items-center gap-3 rounded-lg p-4 text-left inset-ring'
const cardTone = (selected: boolean) =>
  selected
    ? 'bg-bg-brand-subtle inset-ring-2 inset-ring-border-brand'
    : 'bg-bg-surface inset-ring-border-default'

type Target = { raceId: number | null; orderId: number | null }

/** 「춘천 호수 하프마라톤 10K」 → 「춘천 호수 하프마라톤 · 10K」 (주문 상품명 끝의 종목을 떼어 보여 준다) */
const ticketTitle = (itemName: string) => itemName.replace(/\s+(\S+)$/, ' · $1')

/** 참가권 하나 (피그마 M42): 대회 · 종목, 그 아래 「11.16 (일) · 신청 완료」. 대회 날이 지났으면 참가 완료 */
function TicketOption({
  ticket,
  selected,
  onSelect,
}: {
  ticket: MyTicket
  selected: boolean
  onSelect: () => void
}) {
  const raceDate = useRaceDate(ticket.raceId)
  const now = useNow()
  const done =
    ticket.itemStatus === 'CANCELED'
      ? '취소'
      : raceDate && now && raceDate.slice(0, 10) < today(now)
        ? '참가 완료'
        : '신청 완료'
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={onSelect}
      className={cn(CARD, 'flex-col items-start gap-1', cardTone(selected), FOCUS_RING)}
    >
      <span className="text-body1-bold">{ticketTitle(ticket.itemName)}</span>
      <span className="text-caption-regular text-text-tertiary">
        {[raceDate && formatRaceDate(raceDate).replace('/', '.'), done].filter(Boolean).join(' · ')}
      </span>
    </button>
  )
}

const today = (now: Date) => now.toLocaleDateString('en-CA', { timeZone: 'Asia/Seoul' })

/**
 * 피그마 문의 · 문의 쓰기 두 갈래.
 * - raceId가 있으면 대회 상세에서 온 문의: 종류 고르기(M37) → 쓰기(M38)
 * - raceId가 없으면 고객센터의 참가 · 결제 문의: 참가권 고르기(M42) → 쓰기(M43). 참가권이 없으면 바로 쓰기(M43-1)
 */
export function InquiryComposePage({ raceId }: { raceId?: number }) {
  return raceId === undefined ? <PaymentFlow /> : <RaceFlow raceId={raceId} />
}

function RaceFlow({ raceId }: { raceId: number }) {
  const raceName = useRaceName(raceId)
  const [type, setType] = useState<InquiryType>()
  const [picked, setPicked] = useState<InquiryType>('RACE_VENUE')

  if (type) {
    return (
      <WriteStep
        title={`${TYPE_LABEL[type]} 문의하기`}
        subtitle={[raceName, ANSWERER_NOTE[ASSIGNEE_OF[type]]].filter(Boolean).join(' · ')}
        type={type}
        target={{ raceId, orderId: null }}
      />
    )
  }
  return (
    <>
      <AppHeader type="sub" title="문의하기" />
      <main className="flex flex-1 flex-col gap-3 px-4 pt-4">
        {raceName && <p className="text-caption-regular text-text-tertiary">{raceName}</p>}
        {TYPE_CHOICES.map((choice) => (
          <button
            key={choice.type}
            type="button"
            aria-pressed={picked === choice.type}
            onClick={() => setPicked(choice.type)}
            className={cn(CARD, cardTone(picked === choice.type), FOCUS_RING)}
          >
            <span className="flex min-w-0 flex-1 flex-col gap-1">
              <span className="text-body1-bold">{TYPE_LABEL[choice.type]}</span>
              <span className="text-caption-regular text-text-tertiary">{choice.hint}</span>
            </span>
            <span className="shrink-0 text-body2-bold">
              {ASSIGNEE_ANSWERER[ASSIGNEE_OF[choice.type]]} 답변
            </span>
          </button>
        ))}
      </main>
      <BottomCta
        className="sticky bottom-0 z-10"
        primary={
          <Button size="l" onClick={() => setType(picked)}>
            다음
          </Button>
        }
      />
    </>
  )
}

function PaymentFlow() {
  const tickets = useQuery(myTicketsQueryOptions())
  // undefined = 아직 고르는 중, null = 참가권 없이 쓰기
  const [target, setTarget] = useState<(Target & { label: string }) | null>()
  const [pickedId, setPickedId] = useState<string>()

  const list = tickets.data
  const noTickets = tickets.isError || list?.length === 0

  if (target !== undefined || noTickets) {
    return (
      <WriteStep
        title="참가 · 결제 문의하기"
        subtitle={[target?.label, ANSWERER_NOTE.ADMIN].filter(Boolean).join(' · ')}
        type="APPLY_PAYMENT"
        target={{ raceId: target?.raceId ?? null, orderId: target?.orderId ?? null }}
      />
    )
  }
  const picked = list?.find((t) => t.id === (pickedId ?? list[0]?.id))
  const choose = (ticket: MyTicket) => setPickedId(ticket.id)
  return (
    <>
      <AppHeader type="sub" title="참가 · 결제 문의하기" />
      <main className="flex flex-1 flex-col gap-3 px-4 pt-4">
        <h2 className="text-title3">어떤 참가권에 대한 문의인가요?</h2>
        {list === undefined ? (
          <div role="status" aria-label="불러오는 중" className="flex flex-col gap-3">
            {Array.from({ length: 3 }, (_, i) => (
              <Skeleton key={i} shape="rect" className="h-16" />
            ))}
          </div>
        ) : (
          list.map((ticket) => (
            <TicketOption
              key={ticket.id}
              ticket={ticket}
              selected={picked?.id === ticket.id}
              onSelect={() => choose(ticket)}
            />
          ))
        )}
      </main>
      <BottomCta
        className="sticky bottom-0 z-10"
        secondary={
          <Button size="l" variant="secondary" onClick={() => setTarget(null)}>
            건너뛰기
          </Button>
        }
        primary={
          <Button
            size="l"
            disabled={!picked}
            onClick={() =>
              picked &&
              setTarget({
                raceId: picked.raceId,
                orderId: picked.orderId,
                label: ticketTitle(picked.itemName),
              })
            }
          >
            다음
          </Button>
        }
      />
    </>
  )
}

// 피그마 M38 · M43 · M43-1: 제목 100자, 내용 1,000자. 둘 다 써야 보낼 수 있다
function WriteStep({
  title,
  subtitle,
  type,
  target,
}: {
  title: string
  subtitle?: string
  type: InquiryType
  target: Target
}) {
  const router = useRouter()
  const toast = useToast()
  const create = useCreateInquiry()
  const [subject, setSubject] = useState('')
  const [body, setBody] = useState('')
  const ready = subject.trim() !== '' && body.trim() !== ''

  const send = () =>
    create.mutate(
      { ...target, type, title: subject.trim(), body: body.trim() },
      {
        onSuccess: () => router.replace('/me/inquiries?sent=1'),
        onError: (error) => toast({ message: inquiryErrorMessage(error), tone: 'error' }),
      },
    )

  return (
    <>
      <AppHeader type="sub" title={title} />
      <main className="flex flex-1 flex-col gap-4 px-4 pt-4">
        {subtitle && <p className="text-caption-regular text-text-tertiary">{subtitle}</p>}
        <TextField
          label="제목"
          value={subject}
          maxLength={TITLE_MAX}
          onChange={(e) => setSubject(e.target.value)}
          helper={`${subject.length}/${TITLE_MAX}`}
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
          <Button size="l" disabled={!ready} loading={create.isPending} onClick={send}>
            문의 보내기
          </Button>
        }
      />
    </>
  )
}
