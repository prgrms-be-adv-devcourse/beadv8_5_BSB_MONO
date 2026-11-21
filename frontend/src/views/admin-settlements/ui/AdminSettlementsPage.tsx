'use client'

import { useState } from 'react'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import { usePlaceHold } from '@/features/payout-hold'
import { formatMonthDay, HOLD_REASON_ITEMS, PAYOUT_STAGE } from '@/entities/payout'
import type { payoutModel } from '@/shared/api'
import { formatWon, useSearchParamsState } from '@/shared/lib'
import { Button, Chip, EmptyState, KeyValue, Select } from '@/shared/ui'

import { PayoutPickerDialog } from './PayoutPickerDialog'

const TABS = ['지급 예정', '지급보류', '지급 완료'] as const
type Tab = (typeof TABS)[number]
const isTab = (value: string): value is Tab => TABS.some((tab) => tab === value)

// 피그마 v3 · 정산 관리. 지급은 매월 20일 자동이라 관리자는 보기와 지급보류 걸기만 한다.
// TODO(api): 관리자 정산 목록 API가 openapi에 없어 목록은 비워 둔다. 생기면 칩 건수와 목록을 연결한다.
export function AdminSettlementsPage() {
  const [{ tab: tabParam }, setParams] = useSearchParamsState({ tab: '지급 예정' })
  const tab: Tab = isTab(tabParam) ? tabParam : '지급 예정'

  return (
    <ConsolePage title="정산 관리">
      <div className="flex items-center gap-2" role="group" aria-label="정산 상태">
        {TABS.map((t) => (
          <Chip key={t} selected={t === tab} onClick={() => setParams({ tab: t })}>
            {t}
          </Chip>
        ))}
      </div>

      <ConsoleCard title={tab}>
        <EmptyState title="정산 목록을 아직 불러올 수 없어요" />
      </ConsoleCard>

      {tab === '지급보류' && <PlaceHoldCard />}
    </ConsolePage>
  )
}

// 보류는 정산 내역 한 건에 걸린다. 번호를 넣게 하지 않고 창에서 대회 이름으로 찾아 고른다
function PlaceHoldCard() {
  const placeHold = usePlaceHold()
  const [picked, setPicked] = useState<payoutModel.PayoutDto | null>(null)
  const [picking, setPicking] = useState(false)
  const [reasonType, setReasonType] = useState<payoutModel.PayoutHoldReason>('대회_취소_처리')

  const submit = () => {
    if (!picked) return
    placeHold.mutate(
      { payoutId: picked.id, data: { reasonType } },
      { onSuccess: () => setPicked(null) },
    )
  }

  return (
    <ConsoleCard title="새 보류 걸기">
      <div className="flex flex-col gap-4">
        {picked ? (
          <div role="group" aria-label="고른 정산" className="flex flex-col gap-2">
            <KeyValue label="대회 이름" value={picked.raceName} />
            <KeyValue label="정산 ID" value={picked.payoutCode} />
            <KeyValue label="정산 단계" value={PAYOUT_STAGE[picked.payoutStage]} />
            <KeyValue label="지급액" value={formatWon(picked.payoutAmount)} />
            <KeyValue label="지급 예정일" value={formatMonthDay(picked.scheduledPayDate)} />
            <Button
              variant="secondary"
              size="s"
              className="self-start"
              onClick={() => setPicking(true)}
            >
              다시 고르기
            </Button>
          </div>
        ) : (
          <Button variant="secondary" className="self-start" onClick={() => setPicking(true)}>
            정산 고르기
          </Button>
        )}
        <Select
          label="보류 사유"
          helper="사유는 판매자 화면에도 보여요"
          items={HOLD_REASON_ITEMS}
          value={reasonType}
          onValueChange={(next) => next && setReasonType(next)}
        />
        <Button
          variant="danger"
          disabled={!picked}
          loading={placeHold.isPending}
          className="self-start"
          onClick={submit}
        >
          보류 걸기
        </Button>
      </div>
      <PayoutPickerDialog
        open={picking}
        onOpenChange={setPicking}
        onPick={(payout) => {
          setPicked(payout)
          setPicking(false)
        }}
      />
    </ConsoleCard>
  )
}
