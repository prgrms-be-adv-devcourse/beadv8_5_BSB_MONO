'use client'

import { useState } from 'react'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import { usePlaceHold } from '@/features/payout-hold'
import { HOLD_REASON_ITEMS } from '@/entities/payout'
import type { payoutModel } from '@/shared/api'
import { cn } from '@/shared/lib'
import {
  Badge,
  Button,
  DataTable,
  Divider,
  FOCUS_RING,
  KeyValue,
  Select,
  StatTile,
  TextField,
  type BadgeTone,
} from '@/shared/ui'

type Hold = {
  /** 보류 해제 API에 넘기는 지급보류 번호 */
  holdId: number
  id: string
  seller: string
  settlementId: string
  amount: string
  reason: string
  status: string
  tone: BadgeTone
}

// TODO(api): 관리자 지급보류 목록 API가 아직 없어(PRO-156 선행 작업) 목록은 시안 예시 값이다.
// holdId도 예시 값이라 실제 보류와 맞지 않아, 예시 행의 보류 해제 버튼은 막아 둔다.
const HOLDS: Hold[] = [
  {
    holdId: 1,
    id: 'HD-20261027-01A2',
    seller: '춘천러닝클럽',
    settlementId: 'ST-20261120-1C07',
    amount: '₩860,000',
    reason: '정산 계좌 미등록',
    status: '지급보류',
    tone: 'warning',
  },
  {
    holdId: 2,
    id: 'HD-20261025-0C13',
    seller: '트레일앤코',
    settlementId: 'ST-20261120-1B07',
    amount: '₩2,400,000',
    reason: '대회 취소 처리',
    status: '지급보류',
    tone: 'warning',
  },
  {
    holdId: 3,
    id: 'HD-20260921-0B71',
    seller: '나이트런컴퍼니',
    settlementId: 'ST-20260921-09B2',
    amount: '₩300,000',
    reason: '정산 계좌 미등록',
    status: '해제',
    tone: 'neutral',
  },
]

/** '12' 같은 양의 정수만 정산 내역 번호로 받는다 */
function parsePayoutId(text: string): number | null {
  return /^[1-9]\d*$/.test(text.trim()) ? Number(text.trim()) : null
}

// 피그마 V2 · A06 지급보류 관리
export function AdminHoldsPage() {
  const [selectedId, setSelectedId] = useState('HD-20261025-0C13')
  const hold = HOLDS.find((h) => h.id === selectedId) ?? HOLDS[1]
  const placeHold = usePlaceHold()
  const [payoutIdText, setPayoutIdText] = useState('')
  const [reasonType, setReasonType] = useState<payoutModel.PayoutHoldReason>('대회_취소_처리')
  const [payoutIdError, setPayoutIdError] = useState<string>()

  const submitHold = () => {
    const payoutId = parsePayoutId(payoutIdText)
    if (payoutId === null) {
      setPayoutIdError('정산 내역 번호를 숫자로 넣어 주세요.')
      return
    }
    setPayoutIdError(undefined)
    placeHold.mutate({ payoutId, data: { reasonType } })
  }

  return (
    <ConsolePage
      title="지급보류 관리"
      description="보류한 금액은 지급 완료로 바꿀 수 없어요. 풀면 해제일 다음 날 지급해요(원래 지급일보다 앞당기지 않아요)."
      // TODO(api): 지급보류 목록 엑셀 내려받기
      actions={<Button variant="secondary">엑셀로 내려받기</Button>}
    >
      <div className="flex gap-4">
        <StatTile label="지급보류 중" value="2" unit="건" />
        <StatTile label="보류 금액" value="₩3,260,000" />
        <StatTile label="이번 달 해제" value="1" unit="건" />
      </div>

      <ConsoleCard
        title={`지급보류 ${HOLDS.length}건`}
        aside={
          <button
            type="button"
            className={cn('rounded-sm text-body2-medium text-text-brand', FOCUS_RING)}
          >
            ID로 찾기
          </button>
        }
      >
        <DataTable
          label="지급보류"
          rows={HOLDS}
          rowKey={(h) => h.id}
          selected={selectedId}
          onRowClick={(h) => setSelectedId(h.id)}
          columns={[
            { key: 'id', header: '지급보류 ID', width: 'w-42.5', render: (h) => h.id },
            { key: 'seller', header: '판매자', width: 'w-35', render: (h) => h.seller },
            {
              key: 'settlementId',
              header: '정산 ID',
              width: 'w-42.5',
              render: (h) => h.settlementId,
            },
            {
              key: 'amount',
              header: '금액',
              align: 'right',
              width: 'w-27.5',
              render: (h) => h.amount,
            },
            { key: 'reason', header: '사유', render: (h) => h.reason },
            {
              key: 'status',
              header: '상태',
              width: 'w-21',
              render: (h) => <Badge tone={h.tone}>{h.status}</Badge>,
            },
          ]}
        />
      </ConsoleCard>

      <ConsoleCard title="보류 처리">
        <div className="flex flex-col gap-3 rounded-md bg-bg-subtle p-4">
          <p className="text-body2-medium">
            {hold.id} · {hold.seller}
          </p>
          <KeyValue label="보류 금액" value={hold.amount} />
          <KeyValue label="사유" value={hold.reason} />
          {/* TODO(api): 관리자 목록 API가 생기면 실제 행에서만 켠다 */}
          <Button variant="secondary" className="w-full" disabled>
            보류 해제
          </Button>
          <p className="text-caption-regular text-text-tertiary">
            해제하면 해제일 다음 날 지급해요(원래 지급일보다 앞당기지 않아요).
          </p>
        </div>
        <Divider />
        <h3 className="text-body1-medium">새 보류 걸기</h3>
        {/* TODO(api): 관리자 정산 목록 API가 생기면 정산 ID(ST-…)로 고르게 바꾼다. 지금은 정산 내역 번호를 직접 넣는다 */}
        <TextField
          label="정산 내역 번호"
          helper="정산 ID(ST-…)로 찾는 기능은 아직 없어 번호를 직접 넣어요"
          inputMode="numeric"
          value={payoutIdText}
          onChange={(e) => setPayoutIdText(e.target.value)}
          error={payoutIdError}
        />
        <Select
          label="보류 사유"
          helper="정산 계좌 미등록 · 대회 취소 처리"
          items={HOLD_REASON_ITEMS}
          value={reasonType}
          onValueChange={(next) => next && setReasonType(next)}
        />
        <Button className="w-full" disabled={placeHold.isPending} onClick={submitHold}>
          보류 걸기
        </Button>
        <p className="text-caption-regular text-text-tertiary">
          판매자 화면에 사유가 그대로 보여요.
        </p>
      </ConsoleCard>
    </ConsolePage>
  )
}
