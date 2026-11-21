'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'

import { formatMonthDayTime, formatSupplyMonth, TAX_INVOICE_STATUS } from '@/entities/payout'
import { payoutApi, unwrapData } from '@/shared/api'
import { formatNumber } from '@/shared/lib'
import { DataTable } from '@/shared/ui'

import { QueryRows, StatusText } from './parts'

export const taxInvoiceHref = (id: number) => `/seller/settlements/tax-invoices/${id}`

// 시안 2070:69773 세금계산서 목록 · 2070:70101 빈 목록
// 목록 API에 기간 조건이 없어 받은 세금계산서를 모두 보여 준다(최근 공급 월부터)
export function TaxInvoiceList() {
  const invoices = payoutApi.useTaxInvoices({ query: { select: unwrapData } })
  const router = useRouter()

  return (
    <QueryRows query={invoices} label="세금계산서" emptyTitle="발행한 세금계산서가 없어요">
      {(rows) => (
        <DataTable
          label="세금계산서"
          rows={rows}
          rowKey={(r) => String(r.id)}
          // 줄 어디를 눌러도 상세로 간다. 키보드는 공급 월 링크로 간다
          onRowClick={(r) => router.push(taxInvoiceHref(r.id) as never)}
          columns={[
            {
              key: 'supplyMonth',
              header: '공급 월',
              width: 'w-30',
              render: (r) => (
                <Link
                  href={taxInvoiceHref(r.id) as never}
                  onClick={(event) => event.stopPropagation()}
                >
                  {formatSupplyMonth(r.supplyMonth)}
                </Link>
              ),
            },
            {
              key: 'total',
              header: '합계',
              align: 'right',
              width: 'w-35',
              render: (r) => <span className="text-body1-bold">{formatNumber(r.totalAmount)}</span>,
            },
            {
              key: 'supply',
              header: '공급가액',
              align: 'right',
              width: 'w-35',
              render: (r) => formatNumber(r.supplyAmount),
            },
            {
              key: 'tax',
              header: '세액',
              align: 'right',
              width: 'w-30',
              render: (r) => formatNumber(r.taxAmount),
            },
            { key: 'approval', header: '승인번호', render: (r) => r.approvalNumber ?? '-' },
            {
              key: 'issuedAt',
              header: '발행 일시',
              width: 'w-40',
              render: (r) => (r.issuedAt ? formatMonthDayTime(r.issuedAt) : '-'),
            },
            {
              key: 'status',
              header: '상태',
              width: 'w-25',
              render: (r) => <StatusText {...TAX_INVOICE_STATUS[r.status]} />,
            },
          ]}
        />
      )}
    </QueryRows>
  )
}
