'use client'

import { useState } from 'react'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import { HOLD_REASON_ITEMS } from '@/entities/payout'
import type { payoutModel } from '@/shared/api'
import { cn } from '@/shared/lib'
import { Button, DataTable, Dialog, FOCUS_RING, KeyValue, Select, StatTile } from '@/shared/ui'

type Settlement = {
  /** 보류 걸기 API에 넘기는 정산 내역 번호 */
  payoutId: number
  /** 보류 중이면 해제 API에 넘기는 지급보류 번호 */
  holdId?: number
  id: string
  seller: string
  amount: string
  hold: string
  status: string
  action: string
}
type RefundRequest = { at: string; member: string; amount: string }

// TODO(api): 관리자 정산 목록 · 환급 API가 아직 없어(PRO-156 선행 작업) 목록은 시안 예시 값이다.
// payoutId · holdId도 예시 값이라 실제 정산과 맞지 않아, 보류 걸기 · 풀기 버튼은 막아 둔다.
const SETTLEMENTS: Settlement[] = [
  {
    payoutId: 1,
    id: 'ST-20261120-0A3F',
    seller: '데브시스터즈러닝',
    amount: '₩1,920,000',
    hold: '—',
    status: '지급 예정',
    action: '지급 완료로 바꾸기',
  },
  {
    payoutId: 2,
    holdId: 1,
    id: 'ST-20261120-1C07',
    seller: '춘천러닝클럽',
    amount: '₩860,000',
    hold: 'HD-20261027-01A2 · 정산 계좌 미등록',
    status: '지급보류',
    action: '보류 해제',
  },
  {
    payoutId: 3,
    id: 'ST-20261120-2D91',
    seller: '나이트런컴퍼니',
    amount: '₩2,616,000',
    hold: '—',
    status: '지급 예정',
    action: '지급 완료로 바꾸기',
  },
]

const REFUNDS: RefundRequest[] = [
  { at: '10/3', member: '한강러너', amount: '₩40,000' },
  { at: '10/4', member: '새벽토끼', amount: '₩15,000' },
]

// 표 안의 처리 글자 버튼
const ACTION = cn('rounded-sm text-body2-regular text-text-primary hover:underline', FOCUS_RING)

// 피그마 V2 · A02 정산 · 환급 처리 (+ A02-1 전체 지급 완료 확인)
export function AdminSettlementsPage({ confirmOpen = false }: { confirmOpen?: boolean }) {
  const [allOpen, setAllOpen] = useState(confirmOpen)
  const [selectedId, setSelectedId] = useState(SETTLEMENTS[0].id)
  const target = SETTLEMENTS.find((r) => r.id === selectedId) ?? SETTLEMENTS[0]
  const [reasonType, setReasonType] = useState<payoutModel.PayoutHoldReason>('대회_취소_처리')

  return (
    <ConsolePage
      title="11/20 정산"
      description="정산 내역을 확인하고 지급 완료로 바꿔요. 실제 송금은 최종 프로젝트에서 붙여요."
      actions={<Button onClick={() => setAllOpen(true)}>전체 지급 완료</Button>}
    >
      <div className="flex gap-6">
        <StatTile label="지급 예정" value="2" unit="건" caption="₩4,536,000" />
        <StatTile label="지급보류" value="1" unit="건" caption="₩860,000" />
      </div>

      <ConsoleCard title="판매자별 정산">
        <DataTable
          label="판매자별 정산"
          rows={SETTLEMENTS}
          rowKey={(r) => r.id}
          selected={selectedId}
          onRowClick={(r) => setSelectedId(r.id)}
          columns={[
            { key: 'id', header: '정산 ID', width: 'w-40', render: (r) => r.id },
            { key: 'seller', header: '판매자', width: 'w-36.5', render: (r) => r.seller },
            {
              key: 'amount',
              header: '지급액',
              align: 'right',
              width: 'w-30',
              render: (r) => r.amount,
            },
            { key: 'hold', header: '지급보류', width: 'w-68', render: (r) => r.hold },
            { key: 'status', header: '상태', width: 'w-29', render: (r) => r.status },
            {
              key: 'action',
              header: '처리',
              // TODO(api): 지급 완료로 바꾸기 API가 아직 없다
              // TODO(api): 관리자 목록 API가 생기면 실제 행에서만 켠다
              render: (r) => (
                <button type="button" className={ACTION} disabled>
                  {r.action}
                </button>
              ),
            },
          ]}
        />
      </ConsoleCard>

      <div className="flex flex-wrap items-stretch gap-6">
        <ConsoleCard title="지급보류 걸기" className="min-w-130 flex-1 items-start">
          <KeyValue label="대상 정산" value={`${target.id} · ${target.seller}`} />
          <Select
            label="보류 사유"
            helper="사유는 판매자 화면에도 보여요"
            items={HOLD_REASON_ITEMS}
            value={reasonType}
            onValueChange={(next) => next && setReasonType(next)}
            className="w-full"
          />
          <p className="text-caption-regular text-text-tertiary">
            보류가 풀리면 해제일 다음 날 지급해요(원래 지급일보다 앞당기지 않아요).
          </p>
          {/* TODO(api): 관리자 목록 API가 생기면 실제 행에서만 켠다 */}
          <Button variant="danger" disabled>
            지급보류 걸기
          </Button>
        </ConsoleCard>

        <ConsoleCard title={`예치금 환급 요청 ${REFUNDS.length}건`} className="min-w-130 flex-1">
          <DataTable
            label="예치금 환급 요청"
            rows={REFUNDS}
            rowKey={(r) => `${r.at}-${r.member}`}
            columns={[
              { key: 'at', header: '요청일', align: 'right', render: (r) => r.at },
              { key: 'member', header: '회원', render: (r) => r.member },
              { key: 'amount', header: '금액', align: 'right', render: (r) => r.amount },
              {
                key: 'action',
                header: '처리',
                align: 'center',
                width: 'w-42.5',
                // TODO(api): 환급 완료로 바꾸기
                render: () => (
                  <button type="button" className={ACTION}>
                    환급 완료로 바꾸기
                  </button>
                ),
              },
            ]}
          />
          <p className="text-caption-regular text-text-tertiary">
            회원 계좌로 보내고 환급 완료로 바꿔요. 1차에서는 실제 송금 없이 상태만 바꿔요.
          </p>
        </ConsoleCard>
      </div>

      <Dialog
        open={allOpen}
        onOpenChange={setAllOpen}
        title="2건을 지급 완료로 바꿀까요?"
        body={
          <>
            지급 예정 2건, ₩4,536,000이에요.
            <br />
            지급보류 1건(₩860,000)은 빠져요.
          </>
        }
        confirmLabel="지급 완료로 바꾸기"
        cancelLabel="취소"
        onConfirm={() => setAllOpen(false)}
      />
    </ConsolePage>
  )
}
