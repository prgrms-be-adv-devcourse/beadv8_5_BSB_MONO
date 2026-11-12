'use client'

import { useQuery } from '@tanstack/react-query'
import Link from 'next/link'

import { AppHeader } from '@/widgets/app-header'
import { raceDetailQueryOptions } from '@/entities/race'
import { useMyBalance } from '@/entities/wallet'
import { orderApi, unwrapData } from '@/shared/api'
import { formatNumber, orderTrackItems, useTrackOnce } from '@/shared/lib'
import { Badge, BibCard, BottomCta, buttonClassName, KeyValue, Skeleton } from '@/shared/ui'

// 피그마 V2 · M08 참가 확정
// 결제한 주문(/checkout/complete?orderId=)을 주문 API로 불러와 그린다. 주문은 로그인이 필요해 클라이언트에서 부른다.
// 배번은 주문 API에 없어 아직 보여 주지 않는다.
export function CheckoutCompletePage({ orderId }: { orderId: number }) {
  const order = orderApi.useFindById1(orderId, { query: { select: unwrapData } })
  const raceId = order.data?.raceId
  const race = useQuery({
    ...raceDetailQueryOptions(String(raceId)),
    enabled: raceId !== undefined,
  })
  const item = order.data?.items?.[0]
  const { balance } = useMyBalance()
  // 새로고침으로 다시 보내도 GA4가 같은 주문 번호(transaction_id)를 한 번으로 센다
  const paid = order.data?.status === 'PAID' ? order.data : undefined
  useTrackOnce(
    'purchase',
    paid
      ? { orderId: String(orderId), items: orderTrackItems(paid), value: paid.totalAmount ?? 0 }
      : null,
  )
  // 참가자 이름은 참가 신청 API로 불러온다 (예매 흐름 스토어는 새로고침하면 비어서 쓰지 않는다)
  const participationId = item?.participationId
  const participation = orderApi.useFindById(participationId ?? 0, {
    query: { enabled: participationId !== undefined, select: unwrapData },
  })

  const option = race.data?.options.find((o) => o.id === String(item?.raceCourseId))
  const participant =
    participation.data?.participant?.name ?? order.data?.shippingAddress?.recipientName
  const refundDeadline = race.data?.schedule.find((row) => row.label === '전액 환불 기한')?.value

  return (
    <>
      <AppHeader type="sub" title="참가 확정" close backHref="/" />
      <main className="flex flex-1 flex-col justify-center-safe gap-8 px-4 pt-16 pb-8">
        {order.data ? (
          <>
            <div className="flex flex-col gap-2 pb-2">
              <h2 className="text-title2">참가가 확정됐어요</h2>
              <p className="text-body2-regular text-text-secondary">
                {race.data && option
                  ? `${race.data.title} · ${option.name}`
                  : (item?.itemName ?? '')}
              </p>
            </div>
            {/* 참가자 이름은 Clarity 화면 녹화에서 가린다 */}
            <div className="contents" data-clarity-mask="true">
              <BibCard
                image={race.data?.images[0]}
                imagePosition="50% 12%"
                eyebrow={race.data?.title ?? ''}
                status={<Badge tone="live">참가 확정</Badge>}
                unit="No."
                number="-"
                title={[participant, option?.name].filter(Boolean).join(' · ')}
                meta={race.data ? `${race.data.startsAt} · ${race.data.place}` : undefined}
              />
            </div>
            <div className="flex flex-col gap-3">
              <KeyValue label="주문번호" value={order.data.orderNo ?? '-'} />
              {participant && (
                <div data-clarity-mask="true">
                  <KeyValue label="참가자" value={participant} />
                </div>
              )}
              <KeyValue
                label="결제"
                value={`${formatNumber(order.data.totalAmount ?? 0)}원 (예치금)`}
              />
              {balance !== undefined && (
                <KeyValue label="남은 예치금" value={`${formatNumber(balance)}원`} />
              )}
              {order.data.shippingAddress && refundDeadline && (
                <KeyValue label="키트 배송" value={`환불 기한(${refundDeadline}) 지난 뒤 배송`} />
              )}
            </div>
          </>
        ) : (
          <div aria-busy className="flex flex-col gap-5">
            <Skeleton shape="rect" className="h-16" />
            <Skeleton shape="rect" className="h-48" />
            <Skeleton shape="rect" className="h-32" />
          </div>
        )}
      </main>
      <BottomCta
        className="sticky bottom-0 z-10"
        secondary={
          <Link href="/" className={buttonClassName({ variant: 'secondary', size: 'l' })}>
            홈으로
          </Link>
        }
        primary={
          <Link href="/tickets" className={buttonClassName({ size: 'l' })}>
            내 참가권 보기
          </Link>
        }
      />
    </>
  )
}
