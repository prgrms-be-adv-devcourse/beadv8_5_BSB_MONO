'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { daysUntil, formatRaceDate, formatRaceDateTime } from '@/entities/race'
import {
  maskPhone,
  TICKET_STATUS,
  ticketErrorMessage,
  ticketNumber,
  useCancelTicket,
} from '@/entities/ticket'
import { formatNumber } from '@/shared/lib'
import { Alert, Badge, BibCard, Button, buttonClassName, Dialog, KeyValue } from '@/shared/ui'

import { fullRefundLastDay } from '../model/refund-period'
import { useTicket } from '../model/use-ticket'
import { TicketStateView, ticketViewState } from './TicketStateView'

// 피그마 V3 · M16 참가권 상세 · 키트 옵션 표시(1528:17414): 카드 상자 대신 회색 띠로 나눈 구역. (+ M16-1 참가 취소 확인)
// TODO(api): 크루 신청 구역은 참가권에 크루 정보가 없고, 고른 키트 옵션은 구매자용 참가 응답에 없어 아직 없다.
// confirmOpen은 시안 미리보기(?preview=confirm)용. 취소 확인 창을 연 채로 보여 준다.
/** 2026-10-31 → 2026-11-01. 날짜만 다루므로 시각대와 상관없다 */
function nextDay(isoDate: string): string {
  const day = new Date(`${isoDate.slice(0, 10)}T00:00:00Z`)
  day.setUTCDate(day.getUTCDate() + 1)
  return day.toISOString()
}

export function TicketDetailPage({
  ticketId,
  confirmOpen = false,
}: {
  ticketId: string
  confirmOpen?: boolean
}) {
  const router = useRouter()
  const data = useTicket(ticketId)
  const cancel = useCancelTicket()
  const [cancelOpen, setCancelOpen] = useState(confirmOpen)
  const viewState = ticketViewState(data)
  const { ticket, order, participation, race, course, status } = data

  if (viewState || !ticket || !order || !race || !status) {
    return (
      <>
        <AppHeader type="sub" title="참가권" backHref="/tickets" />
        <main className="flex flex-1 flex-col gap-4 px-4 py-6">
          <TicketStateView state={viewState ?? 'loading'} />
        </main>
      </>
    )
  }

  const participant = participation?.participant
  const closeAt = race.applicationCloseAt
  const editable = status === 'confirmed' && closeAt !== undefined && new Date(closeAt) > new Date()
  const refundDeadline = race.refundDeadline
  const refundLastDay = fullRefundLastDay(refundDeadline, race.scheduleChangedAt)
  const refundable =
    status === 'confirmed' && refundLastDay !== undefined && daysUntil(refundLastDay) >= 0
  const amount = `${formatNumber(ticket.amount)}원`
  const kitNote = course?.kitIncluded ? '키트도 함께 취소돼요.' : undefined
  return (
    <>
      <AppHeader type="sub" title="참가권" backHref="/tickets" />
      <main className="flex flex-1 flex-col pb-6">
        {/* 참가자 이름 · 연락처는 Clarity 화면 녹화에서 가린다 */}
        <div className="px-4 py-4" data-clarity-mask="true">
          <BibCard
            image={data.image}
            imagePosition="50% 12%"
            eyebrow={race.title ?? ticket.itemName}
            status={<Badge tone={TICKET_STATUS[status].tone}>{TICKET_STATUS[status].label}</Badge>}
            unit="No."
            number={ticketNumber(ticket.orderNo)}
            title={[participant?.name, course?.name ?? ticket.itemName].filter(Boolean).join(' · ')}
            meta={[
              `주문번호 ${ticket.orderNo}`,
              race.raceDate ? formatRaceDateTime(race.raceDate) : undefined,
            ]
              .filter(Boolean)
              .join(' · ')}
          />
        </div>

        <section
          className="flex flex-col gap-3 border-t-8 border-bg-subtle px-4 py-6"
          data-clarity-mask="true"
        >
          <div className="flex items-center justify-between">
            <h2 className="text-[18px] leading-6.5 font-bold tracking-[-0.015em]">참가자 정보</h2>
            {editable && (
              <Link
                href={`/tickets/${ticket.id}/edit`}
                className={buttonClassName({ variant: 'ghost', size: 's' })}
              >
                고치기
              </Link>
            )}
          </div>
          <div className="flex flex-col gap-1 text-body2-regular text-text-secondary">
            {participant?.tshirtSize && <p>사이즈 {participant.tshirtSize}</p>}
            {participant?.emergencyContactPhone && (
              <p>비상 연락처 {maskPhone(participant.emergencyContactPhone)}</p>
            )}
          </div>
        </section>

        {refundDeadline && (
          <section className="flex flex-col gap-3 border-t-8 border-bg-subtle px-4 py-6">
            <h2 className="text-[18px] leading-6.5 font-bold tracking-[-0.015em]">환불 기준</h2>
            <div className="flex flex-col gap-2">
              <KeyValue
                label={`~ ${formatRaceDate(refundDeadline)} 23:59${refundable ? ' (지금)' : ''}`}
                value="전액 환불"
              />
              <KeyValue
                label={`${formatRaceDate(nextDay(refundDeadline))} 00:00 ~`}
                value="개인 사정 환불 불가"
              />
            </div>
          </section>
        )}

        {cancel.isError && (
          <Alert
            className="mx-4 w-auto"
            tone="danger"
            title="참가를 취소하지 못했어요"
            description={ticketErrorMessage(cancel.error)}
          />
        )}

        {refundable && (
          <Alert
            className="mx-4 mt-4 w-auto"
            title={`오늘 취소하면 ${amount}을 예치금으로 돌려받아요`}
            description={kitNote}
          />
        )}

        {status === 'confirmed' && (
          <div className="flex flex-col gap-2 px-4 pt-6">
            <Button
              variant="danger"
              size="l"
              className="w-full"
              onClick={() => setCancelOpen(true)}
            >
              참가 취소하기
            </Button>
          </div>
        )}
      </main>
      <Dialog
        open={cancelOpen}
        onOpenChange={setCancelOpen}
        type="danger"
        title="참가를 취소할까요?"
        body={[`${amount}을 예치금으로 돌려받아요.`, kitNote, '되돌릴 수 없어요.']
          .filter(Boolean)
          .join(' ')}
        confirmLabel="취소하기"
        cancelLabel="그대로 두기"
        confirming={cancel.isPending}
        onConfirm={() =>
          cancel.mutate(
            { orderId: ticket.orderId, orderItemId: ticket.orderItemId },
            {
              onSuccess: () => router.replace(`/tickets/${ticket.id}/canceled`),
              onError: () => setCancelOpen(false),
            },
          )
        }
      />
    </>
  )
}
