'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useState, type ReactNode } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { BottomNav } from '@/widgets/bottom-nav'
import { needsRecordCert, RecordCertEntry } from '@/features/record-cert'
import { daysUntil, formatRaceDate, formatRaceDateTime } from '@/entities/race'
import {
  maskPhone,
  TICKET_STATUS,
  ticketErrorMessage,
  ticketNumber,
  useCancelTicket,
} from '@/entities/ticket'
import { orderModel } from '@/shared/api'
import { cn, formatNumber, useNow } from '@/shared/lib'
import { Alert, BibCard, Dialog, FOCUS_RING } from '@/shared/ui'

import { fullRefundLastDay } from '../model/refund-period'
import { useTicket } from '../model/use-ticket'
import { TicketStateView, ticketViewState } from './TicketStateView'

// 피그마 V3 · 참가권 상세(1944:20815): 배번표 카드 + 참가자 정보 상자 + 취소·환불 상자. (+ M16-1 참가 취소 확인)
// TODO(api): 크루 신청 구역은 참가권에 크루 정보가 없고, 고른 키트 옵션(티셔츠 · 양말)은 구매자용 참가 응답에 없어 티셔츠 사이즈만 보여 준다.
// confirmOpen은 시안 미리보기(?preview=confirm)용. 취소 확인 창을 연 채로 보여 준다.
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
  const now = useNow()
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
  const editable =
    status === 'confirmed' && closeAt !== undefined && !!now && new Date(closeAt) > now
  const refundDeadline = race.refundDeadline
  const refundLastDay = fullRefundLastDay(refundDeadline, race.scheduleChangedAt)
  const refundable =
    status === 'confirmed' &&
    refundLastDay !== undefined &&
    !!now &&
    daysUntil(refundLastDay, now) >= 0
  const amount = `${formatNumber(ticket.amount)}원`
  const kitNote = course?.kitIncluded ? '키트도 함께 취소돼요.' : undefined
  const address = order.shippingAddress
  const addressLine = [address?.address, address?.addressDetail].filter(Boolean).join(' ')
  return (
    <>
      <AppHeader type="sub" title="참가권" backHref="/tickets" className="border-b-0" />
      <main className="flex flex-1 flex-col pb-10">
        {/* 참가자 이름 · 연락처는 Clarity 화면 녹화에서 가린다 */}
        <div className="px-5 py-4" data-clarity-mask="true">
          <BibCard
            image={data.image}
            imagePosition="50% 12%"
            eyebrow={race.title ?? ticket.itemName}
            status={
              status !== 'confirmed' && (
                <span className="text-body2-medium text-text-on-brand">
                  {TICKET_STATUS[status].label}
                </span>
              )
            }
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

        <Section title="참가자 정보" mask>
          <Box
            footer={
              editable && (
                <Link
                  href={`/tickets/${ticket.id}/edit`}
                  className={cn(FOOTER, 'text-text-primary', FOCUS_RING)}
                >
                  참가자 정보 수정하기
                </Link>
              )
            }
          >
            {participant?.tshirtSize && (
              <InfoRow label="티셔츠 사이즈">{participant.tshirtSize}</InfoRow>
            )}
            {addressLine && <InfoRow label="키트 배송지">{addressLine}</InfoRow>}
            {participant?.emergencyContactPhone && (
              <InfoRow label="비상 연락처">
                {[participant.emergencyContactName, maskPhone(participant.emergencyContactPhone)]
                  .filter(Boolean)
                  .join(' · ')}
              </InfoRow>
            )}
          </Box>
        </Section>

        {ticket.itemStatus === orderModel.OrderItemDtoStatus.PAID &&
          !ticket.paying &&
          needsRecordCert(course) && (
            <RecordCertEntry
              orderItemId={ticket.orderItemId}
              href={`/tickets/${ticket.id}/record-cert`}
            />
          )}

        {cancel.isError && (
          <Alert
            className="mx-5 mt-4 w-auto"
            tone="danger"
            title="참가를 취소하지 못했어요"
            description={ticketErrorMessage(cancel.error)}
          />
        )}

        {status === 'confirmed' && (
          <Section title="취소·환불">
            <Box
              footer={
                <button
                  type="button"
                  onClick={() => setCancelOpen(true)}
                  className={cn(FOOTER, 'w-full text-text-danger', FOCUS_RING)}
                >
                  참가 취소하기
                </button>
              }
            >
              {refundable && (
                <p className="text-body1-bold">
                  지금 취소하면 <span className="text-text-brand">{amount}</span> 돌려받아요
                </p>
              )}
              {refundLastDay && (
                <>
                  <InfoRow label="전액 환불">
                    <p>
                      <span className="font-bold text-text-brand">
                        {formatRaceDate(refundLastDay)} 23:59
                      </span>
                      까지 취소
                    </p>
                  </InfoRow>
                  <InfoRow label="그 뒤에 취소">
                    <span className="font-bold text-text-danger">환불 불가</span>
                  </InfoRow>
                </>
              )}
              <InfoRow label="돌려받는 곳">예치금</InfoRow>
            </Box>
          </Section>
        )}
      </main>
      <BottomNav />
      <Dialog
        open={cancelOpen}
        onOpenChange={setCancelOpen}
        type="danger"
        title="참가를 취소할까요?"
        body={[
          refundable ? `${amount}을 예치금으로 돌려받아요.` : '돌려받는 금액이 없어요.',
          kitNote,
          '되돌릴 수 없어요.',
        ]
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

const FOOTER = 'flex justify-center border-t border-border-default py-3.5 text-body2-medium'

function Section({
  title,
  mask,
  children,
}: {
  title: string
  mask?: boolean
  children: ReactNode
}) {
  return (
    <section
      className="flex flex-col gap-3 px-5 pt-8"
      data-clarity-mask={mask ? 'true' : undefined}
    >
      <h2 className="text-[18px] leading-7 font-bold">{title}</h2>
      {children}
    </section>
  )
}

function Box({ children, footer }: { children: ReactNode; footer?: ReactNode }) {
  return (
    <div className="flex flex-col overflow-hidden rounded-md border border-border-default">
      <div className="flex flex-col gap-3 p-4">{children}</div>
      {footer}
    </div>
  )
}

function InfoRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex gap-3 text-body2-regular">
      <span className="w-22 shrink-0 text-text-secondary">{label}</span>
      <div className="min-w-0 flex-1 font-medium text-text-primary">{children}</div>
    </div>
  )
}
