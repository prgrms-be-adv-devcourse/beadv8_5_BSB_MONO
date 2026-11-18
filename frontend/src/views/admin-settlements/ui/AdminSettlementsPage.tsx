'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { Controller, useForm } from 'react-hook-form'
import { z } from 'zod'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import { usePlaceHold } from '@/features/payout-hold'
import { HOLD_REASON_ITEMS } from '@/entities/payout'
import { useSearchParamsState } from '@/shared/lib'
import { Button, Chip, EmptyState, Select, TextField } from '@/shared/ui'

const TABS = ['지급 예정', '지급보류', '지급 완료'] as const
type Tab = (typeof TABS)[number]
const isTab = (value: string): value is Tab => TABS.some((tab) => tab === value)

const holdSchema = z.object({
  // '12' 같은 양의 정수만 정산 내역 번호로 받는다
  payoutId: z
    .string()
    .trim()
    .regex(/^[1-9]\d*$/, '정산 내역 번호를 숫자로 넣어 주세요.'),
  reasonType: z.enum(['대회_취소_처리', '정산_계좌_미등록']),
})
type HoldValues = z.infer<typeof holdSchema>

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

// 목록에서 고를 수 없어 정산 내역 번호를 직접 넣는다
function PlaceHoldCard() {
  const placeHold = usePlaceHold()
  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<HoldValues>({
    resolver: zodResolver(holdSchema),
    defaultValues: { payoutId: '', reasonType: '대회_취소_처리' },
  })

  const onSubmit = handleSubmit(({ payoutId, reasonType }) =>
    placeHold.mutate({ payoutId: Number(payoutId), data: { reasonType } }),
  )

  return (
    <ConsoleCard title="새 보류 걸기">
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <TextField
          label="정산 내역 번호"
          helper="정산 ID(ST-…)로 찾는 기능은 아직 없어 번호를 직접 넣어요"
          inputMode="numeric"
          error={errors.payoutId?.message}
          {...register('payoutId')}
        />
        <Controller
          control={control}
          name="reasonType"
          render={({ field }) => (
            <Select
              label="보류 사유"
              helper="사유는 판매자 화면에도 보여요"
              items={HOLD_REASON_ITEMS}
              value={field.value}
              onValueChange={(next) => next && field.onChange(next)}
            />
          )}
        />
        <Button
          type="submit"
          variant="danger"
          disabled={placeHold.isPending}
          className="self-start"
        >
          보류 걸기
        </Button>
      </form>
    </ConsoleCard>
  )
}
