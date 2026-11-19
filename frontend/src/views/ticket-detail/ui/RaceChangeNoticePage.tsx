'use client'

import { useRouter } from 'next/navigation'

import { AppHeader } from '@/widgets/app-header'
import { daysUntil, formatRaceDate, formatRaceDateTime } from '@/entities/race'
import { ticketErrorMessage, useCancelTicket } from '@/entities/ticket'
import { formatNumber } from '@/shared/lib'
import { Alert, Badge, BottomCta, Button, KeyValue } from '@/shared/ui'

import { fullRefundLastDay } from '../model/refund-period'
import { useTicket } from '../model/use-ticket'
import { TicketBox as Box, TicketSection as Section } from './TicketInfo'
import { TicketStateView, ticketViewState } from './TicketStateView'

// 피그마 v4 · 대회 변경 안내(2070:35538, 일정 변경 알림에서 들어온다). 구역 제목 아래 테두리 상자.
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
          </div>
        ) : (
          <Alert className="mx-4 mt-4 w-auto" title="바뀐 대회 일정이 없어요" />
        )}

        <Section title="바뀐 일정">
          <Box>
            {race.raceDate && (
              <div className="flex flex-col gap-1 border-b border-border-default pb-3">
                <span className="text-caption-regular text-text-secondary">새 날짜</span>
                <span className="font-display text-number-s">
                  {formatRaceDate(race.raceDate).split(' ')[0].replace('/', '.')}
                </span>
                <span className="text-body1-regular text-text-secondary">
                  {formatRaceDateTime(race.raceDate).split(' ').slice(1).join(' ')}
                </span>
              </div>
            )}
            {race.location && <KeyValue label="장소" value={race.location} />}
          </Box>
        </Section>

        {lastDay && (
          <Section title="전액 환불 기간">
            <Box>
              {daysLeft >= 0 ? (
                <p className="flex items-baseline gap-2">
                  <span className="font-display text-number-s">{daysLeft}</span>
                  <span className="text-body1-regular">일 남았어요</span>
                </p>
              ) : (
                <p className="text-body1-bold">전액 환불 기간이 지났어요</p>
              )}
              <p className="text-body1-regular">
                {formatRaceDate(lastDay)} 23:59까지 전액 환불돼요
              </p>
            </Box>
          </Section>
        )}

        <Section title="내 참가권" mask>
          <Box>
            <KeyValue label="종목" value={course?.name ?? ticket.itemName} />
            <KeyValue label="참가자" value={participation?.participant?.name ?? '-'} />
            <KeyValue label="결제 금액" value={<span className="font-bold">{amount}</span>} />
            {refundable && (
              <KeyValue
                label="환불받을 금액"
                value={
                  <>
                    <span className="font-bold">{amount}</span>{' '}
                    <span className="text-caption-regular text-text-secondary">예치금</span>
                  </>
                }
              />
            )}
          </Box>
        </Section>

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
              그대로 참가하기
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
