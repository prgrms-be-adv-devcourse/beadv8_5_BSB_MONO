'use client'

import { useRouter } from 'next/navigation'

import { AppHeader } from '@/widgets/app-header'
import { daysUntil, formatRaceDate, formatRaceDateTime } from '@/entities/race'
import { ticketErrorMessage, useCancelTicket } from '@/entities/ticket'
import { formatNumber } from '@/shared/lib'
import { Alert, Badge, BottomCta, Button, KeyValue, ProgressBar } from '@/shared/ui'

import { fullRefundLastDay, SCHEDULE_CHANGE_REFUND_DAYS } from '../model/refund-period'
import { useTicket } from '../model/use-ticket'
import { TicketStateView, ticketViewState } from './TicketStateView'

// 피그마 V2 · M24 대회 변경 안내(1333:22003, 일정 변경 알림에서 들어온다). 카드 상자 대신 회색 띠로 나눈 구역이다.
// 대회 상세의 scheduleChangedAt(알린 일시) · refundDeadline(환불 기한)으로 그린다.
// TODO(api): 시안의 「이전 날짜」는 대회 API가 바뀌기 전 날짜를 주지 않아 새 날짜만 보여 준다.
export function RaceChangeNoticePage({ ticketId }: { ticketId: string }) {
  const router = useRouter()
  const data = useTicket(ticketId)
  const cancel = useCancelTicket()
  const viewState = ticketViewState(data)
  const { ticket, participation, race, course, status } = data

  if (viewState || !ticket || !race) {
    return (
      <>
        <AppHeader type="sub" title="대회 일정 변경" />
        <main className="flex flex-1 flex-col gap-4 px-4 py-6">
          <TicketStateView state={viewState ?? 'loading'} />
        </main>
      </>
    )
  }

  const changedAt = race.scheduleChangedAt
  const lastDay = fullRefundLastDay(race.refundDeadline, changedAt)
  const daysLeft = lastDay ? daysUntil(lastDay) : -1
  const refundable = status === 'confirmed' && daysLeft >= 0
  const amount = `${formatNumber(ticket.amount)}원`

  return (
    <>
      <AppHeader type="sub" title="대회 일정 변경" />
      <main className="flex flex-1 flex-col pb-6">
        {changedAt ? (
          <div className="flex flex-col items-start gap-2 px-4 py-4">
            <Badge tone="warning">일정 변경</Badge>
            <h2 className="text-title2">{race.title} 날짜가 바뀌었어요</h2>
            <p className="text-body2-regular text-text-secondary">
              {formatRaceDate(changedAt)}에 주최 측이 대회 일정을 바꿨어요.
            </p>
          </div>
        ) : (
          <Alert className="mx-4 mt-4 w-auto" title="바뀐 대회 일정이 없어요" />
        )}

        <section className="flex flex-col gap-4 px-4 py-6">
          {race.raceDate && (
            <div className="flex flex-col gap-1">
              <span className="text-caption-regular text-text-secondary">새 날짜</span>
              <span className="font-display text-number-m">{formatRaceDate(race.raceDate)}</span>
              <span className="text-caption-regular text-text-secondary">
                {formatRaceDateTime(race.raceDate)}
              </span>
            </div>
          )}
          {race.location && <KeyValue label="장소" value={race.location} />}
        </section>

        {lastDay && (
          <section className="flex flex-col gap-3 border-t-8 border-bg-subtle px-4 py-6">
            <p className="text-body2-medium">전액 환불받을 수 있는 기간</p>
            {daysLeft >= 0 ? (
              <>
                <p className="flex items-baseline gap-2">
                  <span className="font-display text-number-m">{daysLeft}</span>
                  <span className="text-body1-medium">일 남았어요</span>
                </p>
                <ProgressBar
                  value={Math.min(100, Math.round((daysLeft / SCHEDULE_CHANGE_REFUND_DAYS) * 100))}
                />
              </>
            ) : (
              <p className="text-body1-medium">전액 환불 기간이 지났어요</p>
            )}
            <p className="text-body2-regular text-text-secondary">
              {[
                changedAt ? `${formatRaceDate(changedAt)} 알림` : undefined,
                `${formatRaceDate(lastDay)} 23:59까지 전액 환불돼요.`,
              ]
                .filter(Boolean)
                .join(' · ')}
            </p>
          </section>
        )}

        <section className="flex flex-col gap-3 border-t-8 border-bg-subtle px-4 py-6">
          <h3 className="text-body1-bold">내 참가권</h3>
          <KeyValue label="종목" value={course?.name ?? ticket.itemName} />
          {/* 참가자 이름은 Clarity 화면 녹화에서 가린다 */}
          <div data-clarity-mask="true">
            <KeyValue label="참가자" value={participation?.participant?.name ?? '-'} />
          </div>
          <KeyValue label="결제 금액" value={amount} />
          {refundable && <KeyValue label="환불받을 금액" value={`${amount} (예치금)`} />}
        </section>

        {cancel.isError && (
          <Alert
            className="mx-4 w-auto"
            tone="danger"
            title="참가를 취소하지 못했어요"
            description={ticketErrorMessage(cancel.error)}
          />
        )}
      </main>
      <BottomCta
        className="sticky bottom-0 z-10"
        secondary={
          refundable ? (
            <Button variant="secondary" size="l" onClick={() => router.replace('/tickets')}>
              그대로 참가
            </Button>
          ) : undefined
        }
        primary={
          refundable ? (
            <Button
              size="l"
              loading={cancel.isPending}
              onClick={() =>
                cancel.mutate(
                  { orderId: ticket.orderId, orderItemId: ticket.orderItemId },
                  { onSuccess: () => router.replace(`/tickets/${ticket.id}/canceled`) },
                )
              }
            >
              전액 환불받기
            </Button>
          ) : (
            <Button size="l" onClick={() => router.replace('/tickets')}>
              내 참가권 보기
            </Button>
          )
        }
      />
    </>
  )
}
