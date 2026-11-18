'use client'

import { useState } from 'react'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import { HOLD_REASON_ITEMS } from '@/entities/payout'
import type { payoutModel } from '@/shared/api'
import { cn, useSearchParamsState } from '@/shared/lib'
import { Button, Chip, DataTable, Dialog, KeyValue, Select } from '@/shared/ui'

type Tab = '지급 예정' | '지급보류' | '지급 완료'
type Settlement = {
  id: string
  seller: string
  amount: string
  /** 지급보류 사유. 보류가 아니면 없다 */
  holdReason?: string
  status: Tab
}

// TODO(api): 관리자 정산 목록 API가 아직 없어(PRO-156 선행 작업) 목록은 시안 예시 값이다.
// 번호도 예시 값이라 실제 정산과 맞지 않아, 보류 걸기 · 풀기 버튼은 막아 둔다.
const SETTLEMENTS: Settlement[] = [
  { id: 'ST-20261120-0A3F', seller: '데브시스터즈러닝', amount: '₩1,920,000', status: '지급 예정' },
  {
    id: 'ST-20261120-1C07',
    seller: '춘천러닝클럽',
    amount: '₩860,000',
    holdReason: '정산 계좌 미등록',
    status: '지급보류',
  },
  { id: 'ST-20261120-2D91', seller: '나이트런컴퍼니', amount: '₩2,616,000', status: '지급 예정' },
  { id: 'ST-20261020-07B4', seller: '나이트런컴퍼니', amount: '₩1,300,000', status: '지급 완료' },
]

const TABS: Tab[] = ['지급 예정', '지급보류', '지급 완료']
const isTab = (value: string): value is Tab => TABS.some((tab) => tab === value)

// 상태는 칩이 아니라 뜻 색 글자로 보여 준다
const STATUS_COLOR: Record<Tab, string> = {
  '지급 예정': 'text-text-secondary',
  지급보류: 'text-text-danger',
  '지급 완료': 'text-text-success',
}

// 피그마 v3 · 정산 관리. 지급은 매월 20일 자동이라 관리자는 보기와 지급보류 걸기 · 풀기만 한다.
export function AdminSettlementsPage() {
  const [{ tab: tabParam }, setParams] = useSearchParamsState({ tab: '지급 예정' })
  const tab: Tab = isTab(tabParam) ? tabParam : '지급 예정'
  const rows = SETTLEMENTS.filter((s) => s.status === tab)
  const [pickedId, setPickedId] = useState(SETTLEMENTS[0].id)
  const target = rows.find((s) => s.id === pickedId) ?? rows[0]
  const [holdOpen, setHoldOpen] = useState(false)
  const [releaseOpen, setReleaseOpen] = useState(false)
  const [reasonType, setReasonType] = useState<payoutModel.PayoutHoldReason>('대회_취소_처리')

  return (
    <ConsolePage title="정산 관리">
      <div className="flex items-center gap-2" role="group" aria-label="정산 상태">
        {TABS.map((t) => (
          <Chip key={t} selected={t === tab} onClick={() => setParams({ tab: t })}>
            {`${t} ${SETTLEMENTS.filter((s) => s.status === t).length}`}
          </Chip>
        ))}
      </div>

      <ConsoleCard title={tab}>
        {rows.length === 0 ? (
          <p className="py-6 text-center text-body2-regular text-text-secondary">
            {tab} 정산이 없어요
          </p>
        ) : (
          <DataTable
            label="판매자별 정산"
            rows={rows}
            rowKey={(r) => r.id}
            selected={target?.id}
            onRowClick={(r) => setPickedId(r.id)}
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
              { key: 'hold', header: '지급보류 사유', render: (r) => r.holdReason ?? '—' },
              {
                key: 'status',
                header: '상태',
                width: 'w-29',
                render: (r) => (
                  <span className={cn('text-body2-medium', STATUS_COLOR[r.status])}>
                    {r.status}
                  </span>
                ),
              },
            ]}
          />
        )}
      </ConsoleCard>

      {target && (
        <ConsoleCard title={`${target.id} · ${target.seller}`} className="items-start">
          <KeyValue label="지급액" value={target.amount} />
          {target.holdReason && <KeyValue label="지급보류 사유" value={target.holdReason} />}
          {/* TODO(api): 관리자 목록 API가 생기면 실제 행에서만 켠다 */}
          {target.status === '지급 예정' && (
            <Button variant="danger" disabled onClick={() => setHoldOpen(true)}>
              지급보류 걸기
            </Button>
          )}
          {target.status === '지급보류' && (
            <Button variant="secondary" disabled onClick={() => setReleaseOpen(true)}>
              지급보류 풀기
            </Button>
          )}
        </ConsoleCard>
      )}

      <Dialog
        open={holdOpen}
        onOpenChange={setHoldOpen}
        title="지급보류를 걸까요?"
        body={
          <Select
            label="보류 사유"
            items={HOLD_REASON_ITEMS}
            value={reasonType}
            onValueChange={(next) => next && setReasonType(next)}
            className="w-full"
          />
        }
        confirmLabel="걸기"
        cancelLabel="취소"
        onConfirm={() => setHoldOpen(false)}
      />
      <Dialog
        open={releaseOpen}
        onOpenChange={setReleaseOpen}
        title="지급보류를 풀까요?"
        body="원래 지급일에 지급해요."
        confirmLabel="풀기"
        cancelLabel="취소"
        onConfirm={() => setReleaseOpen(false)}
      />
    </ConsolePage>
  )
}
