'use client'

import type { Route } from 'next'

import { AppHeader } from '@/widgets/app-header'
import { uploadFile } from '@/features/file-upload'
import { needsRecordCert, RECORD_CERT_FILE_TYPE, RecordCertFlow } from '@/features/record-cert'
import { orderModel } from '@/shared/api'

import { useTicket } from '../model/use-ticket'
import { TicketStateView, ticketViewState } from './TicketStateView'

// 기록증 제출(BUK-279). 결제 완료이고 기록증 규칙이 있는 종목의 참가권만 연다. 이미 낸 제출이 진행 중이면 서버가 409로 막는다.
export function RecordCertPage({ ticketId }: { ticketId: string }) {
  const data = useTicket(ticketId)
  const { ticket, course } = data
  const eligible =
    ticket?.itemStatus === orderModel.OrderItemDtoStatus.PAID &&
    !ticket.paying &&
    needsRecordCert(course)
  const viewState =
    ticketViewState(data) ?? (ticket && course && !eligible ? 'notFound' : undefined)

  return (
    <>
      <AppHeader type="sub" title="기록증 올리기" backHref={`/tickets/${ticketId}` as Route} />
      <main className="flex flex-1 flex-col">
        {viewState || !ticket || !eligible ? (
          <div className="flex flex-1 flex-col gap-4 px-4 py-6">
            <TicketStateView state={viewState ?? 'loading'} />
          </div>
        ) : (
          <RecordCertFlow
            orderItemId={ticket.orderItemId}
            doneHref={`/tickets/${ticketId}`}
            uploadPhoto={async (file) => (await uploadFile(file, RECORD_CERT_FILE_TYPE)).fileId}
          />
        )}
      </main>
    </>
  )
}
