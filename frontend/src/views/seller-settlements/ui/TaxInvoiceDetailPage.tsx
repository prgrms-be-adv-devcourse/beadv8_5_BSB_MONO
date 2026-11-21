'use client'

import type { ReactNode } from 'react'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import { formatDateTimeWithWeekday, supplyMonthTitle, TAX_INVOICE_STATUS } from '@/entities/payout'
import { payoutApi, unwrapData } from '@/shared/api'
import { formatNumber } from '@/shared/lib'
import { Alert, KeyValue, Skeleton } from '@/shared/ui'

import { errorText, Named, PillLink, StatusText } from './parts'

// 시안 2070:70160 발행 완료 · 2070:70207 발행 실패. 폭 720
// 세금계산서 한 장 조회 API가 없어 목록(판매자 것 전부)에서 ID로 찾는다
export function TaxInvoiceDetailPage({ invoiceId }: { invoiceId: string }) {
  const invoices = payoutApi.useTaxInvoices({ query: { select: unwrapData } })
  const invoice = invoices.data?.find((i) => String(i.id) === invoiceId)

  return (
    <ConsolePage
      title="세금계산서"
      className="max-w-180"
      backLink={<PillLink href="/seller/settlements?tab=taxInvoices">세금계산서</PillLink>}
      description={invoice && supplyMonthTitle(invoice.supplyMonth)}
    >
      {invoices.isError ? (
        <Alert
          tone="danger"
          title="세금계산서를 불러오지 못했어요"
          description={errorText(invoices.error)}
        />
      ) : !invoices.data ? (
        <Skeleton className="h-80 w-full" />
      ) : !invoice ? (
        <Alert tone="danger" title="세금계산서를 찾을 수 없어요" />
      ) : (
        <>
          <div className="flex flex-col gap-1">
            <StatusText {...TAX_INVOICE_STATUS[invoice.status]} />
            {invoice.status === '발행_실패' && (
              <p className="text-caption-regular text-text-secondary">다음 정산 때 다시 발행돼요</p>
            )}
          </div>

          <InfoCard
            title="금액"
            rows={[
              ['합계', <b key="total">{formatNumber(invoice.totalAmount)}원</b>],
              ['공급가액', `${formatNumber(invoice.supplyAmount)}원`],
              ['세액', `${formatNumber(invoice.taxAmount)}원`],
            ]}
          />
          <InfoCard
            title="공급받는 자"
            rows={[
              ['상호', invoice.businessName ?? '-'],
              ['사업자등록번호', invoice.businessNumber ?? '-'],
              ['대표자', invoice.representativeName ?? '-'],
            ]}
          />
          <InfoCard
            title="발행 정보"
            rows={[
              invoice.status === '발행_실패'
                ? [
                    '실패 사유',
                    <span key="fail" className="text-text-danger">
                      {invoice.failReason ?? '-'}
                    </span>,
                  ]
                : ['승인번호', invoice.approvalNumber ?? '-'],
              ['발행 일시', invoice.issuedAt ? formatDateTimeWithWeekday(invoice.issuedAt) : '-'],
            ]}
          />
        </>
      )}
    </ConsolePage>
  )
}

function InfoCard({ title, rows }: { title: string; rows: [string, ReactNode][] }) {
  return (
    <Named label={title}>
      <ConsoleCard title={title} className="gap-2 p-6.25">
        <div className="flex flex-col">
          {rows.map(([label, value]) => (
            <KeyValue key={label} className="h-12" label={label} value={value} />
          ))}
        </div>
      </ConsoleCard>
    </Named>
  )
}
