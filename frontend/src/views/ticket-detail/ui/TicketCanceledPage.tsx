'use client'

import Link from 'next/link'

import { AppHeader } from '@/widgets/app-header'
import { useMyBalance } from '@/entities/wallet'
import { formatNumber } from '@/shared/lib'
import { BottomCta, buttonClassName, KeyValue } from '@/shared/ui'

import { useTicket } from '../model/use-ticket'
import { TicketStateView, ticketViewState } from './TicketStateView'

// 피그마 V2 · M16-2 참가 취소 완료(1333:21935)
// TODO(api): 시안의 「취소 일시」는 주문 API가 취소한 시각을 주지 않아 보여 주지 않는다.
export function TicketCanceledPage({ ticketId }: { ticketId: string }) {
  const data = useTicket(ticketId)
  const wallet = useMyBalance()
  const viewState = ticketViewState(data)
  const { ticket, order, race, course } = data

  return (
    <>
      <AppHeader type="sub" title="취소 완료" close backHref="/tickets" />
      <main className="flex flex-1 flex-col justify-center-safe gap-6 px-4 pt-6 pb-8">
        {viewState || !ticket || !order ? (
          <TicketStateView state={viewState ?? 'loading'} />
        ) : (
          <>
            <div className="flex flex-col gap-2">
              <h2 className="text-title2">참가를 취소했어요</h2>
              <p className="text-body2-regular text-text-secondary">
                {race?.title && course?.name ? `${race.title} · ${course.name}` : ticket.itemName}
              </p>
            </div>
            <div className="flex flex-col gap-3">
              <KeyValue label="주문번호" value={ticket.orderNo} />
              <KeyValue label="돌려받은 금액" value={`${formatNumber(ticket.amount)}원 (예치금)`} />
              <KeyValue
                label="지금 예치금"
                value={wallet.balance === undefined ? '-' : `${formatNumber(wallet.balance)}원`}
              />
              {course?.kitIncluded && <KeyValue label="키트" value="함께 취소됨" />}
            </div>
          </>
        )}
      </main>
      <BottomCta
        className="sticky bottom-0 z-10"
        secondary={
          <Link
            href="/me/deposit"
            replace
            className={buttonClassName({ variant: 'secondary', size: 'l' })}
          >
            예치금 보기
          </Link>
        }
        primary={
          <Link href="/tickets" replace className={buttonClassName({ size: 'l' })}>
            내 참가권 보기
          </Link>
        }
      />
    </>
  )
}
