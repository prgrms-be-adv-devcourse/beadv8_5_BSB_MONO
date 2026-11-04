'use client'

import { useQuery } from '@tanstack/react-query'
import { Fragment, useState } from 'react'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import { myRacesQueryOptions } from '@/entities/race'
import {
  KIT_FLOW,
  KIT_STATUS,
  kitErrorMessage,
  shortAddress,
  useCompleteKitDeliveries,
  useSellerKits,
  useSellerKitSummary,
  useShipKits,
  type KitFailure,
  type KitFilters,
  type KitStatus,
} from '@/entities/seller-kit'
import { orderModel } from '@/shared/api'
import { cn } from '@/shared/lib'
import {
  Alert,
  Badge,
  Button,
  Checkbox,
  Chip,
  DataTable,
  EmptyState,
  FOCUS_RING,
  Select,
  Skeleton,
  StatTile,
  Textarea,
  TextField,
} from '@/shared/ui'

type Kit = orderModel.SellerKitDto

/** 발송 · 배송 완료로 바꿀 수 있는 다음 단계 (배송 준비로 되돌리는 API는 없다) */
type NextStatus = Exclude<KitStatus, 'PREPARING'>

const ALL = ''
const STATUS_ITEMS = [
  { value: ALL, label: '전체' },
  ...KIT_FLOW.map((s) => ({ value: s, label: KIT_STATUS[s].label })),
]
const CARRIERS = ['CJ대한통운', '롯데택배', '한진택배', '우체국택배'].map((c) => ({
  value: c,
  label: c,
}))

// 버튼 문구의 조사 (배송 중으로 · 배송 완료로)
const TO: Record<KitStatus, string> = {
  PREPARING: '배송 준비로',
  SHIPPING: '배송 중으로',
  DELIVERED: '배송 완료로',
}

/** 그 단계로 바꾸려면 지금 이 단계여야 한다 */
const FROM: Record<NextStatus, KitStatus> = { SHIPPING: 'PREPARING', DELIVERED: 'SHIPPING' }

function kitId(kit: Kit) {
  return kit.orderItemId ?? 0
}

function kitOption(kit: Kit) {
  return [kit.courseName, kit.tshirtSize && `티셔츠 ${kit.tshirtSize}`].filter(Boolean).join(' · ')
}

/** 송장 번호를 줄마다 하나씩 읽는다 (빈 줄은 건너뛴다) */
function trackingLines(text: string) {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
}

// 피그마 V2 · S08 키트 배송 관리
export function SellerKitsPage() {
  const [raceDraft, setRaceDraft] = useState(ALL)
  const [statusDraft, setStatusDraft] = useState(ALL)
  const [filters, setFilters] = useState<KitFilters>({})

  const races = useQuery(myRacesQueryOptions())
  const kits = useSellerKits(filters)
  const summary = useSellerKitSummary(filters.raceId)

  const [checked, setChecked] = useState<number[]>([])
  const [next, setNext] = useState<NextStatus>('SHIPPING')
  const [carrier, setCarrier] = useState(CARRIERS[0].value)
  const [tracking, setTracking] = useState('')
  const [result, setResult] = useState<{ done: number; failures: KitFailure[] } | null>(null)

  const ship = useShipKits()
  const deliver = useCompleteKitDeliveries()
  const pending = ship.isPending || deliver.isPending

  const rows = kits.data?.kits ?? []
  const selected = rows.filter((kit) => checked.includes(kitId(kit)))
  const raceItems = [
    { value: ALL, label: '전체 대회' },
    ...(races.data ?? []).map((race) => ({ value: String(race.raceId), label: race.title ?? '' })),
  ]

  const toggle = (id: number, on: boolean) =>
    setChecked((prev) => (on ? [...prev, id] : prev.filter((key) => key !== id)))

  const search = () => {
    setFilters({
      raceId: raceDraft === ALL ? undefined : Number(raceDraft),
      status: statusDraft === ALL ? undefined : (statusDraft as orderModel.FindKitsStatus),
    })
    setChecked([])
    setResult(null)
  }

  const lines = trackingLines(tracking)
  const selectedLabels = [
    ...new Set(selected.map((kit) => KIT_STATUS[kit.kitDeliveryStatus ?? 'PREPARING'].label)),
  ]
  const blocker =
    selected.length === 0
      ? null
      : selected.some((kit) => kit.kitDeliveryStatus !== FROM[next])
        ? `${KIT_STATUS[FROM[next]].label} 상태인 주문만 ${TO[next]} 바꿀 수 있어요.`
        : next === 'SHIPPING' && lines.length !== selected.length
          ? `송장 번호를 선택한 ${selected.length}건만큼 줄마다 넣어 주세요.`
          : null
  const canSubmit = selected.length > 0 && !blocker && !pending

  async function submit() {
    setResult(null)
    const outcome =
      next === 'SHIPPING'
        ? await ship.mutateAsync(
            selected.map((kit, i) => ({
              orderItemId: kitId(kit),
              carrier,
              trackingNumber: lines[i],
            })),
          )
        : await deliver.mutateAsync(selected.map((kit) => ({ orderItemId: kitId(kit) })))
    const failedIds = outcome.failures.map((f) => f.orderItemId)
    setResult({ done: outcome.total - failedIds.length, failures: outcome.failures })
    // 성공한 건은 고른 상태에서 빼고, 실패한 건만 남겨 다시 시도하게 한다
    setChecked((prev) => prev.filter((id) => failedIds.includes(id)))
    if (failedIds.length === 0) setTracking('')
  }

  const orderNoOf = (id: number) => rows.find((kit) => kitId(kit) === id)?.orderNo ?? String(id)

  return (
    <ConsolePage
      title="키트 배송 관리"
      description="키트는 환불 기한이 지난 뒤 배송해요. 배송 상태는 판매자가 바꾸고, 송장 번호를 넣으면 배송 중이 돼요."
      // TODO(api): 배송 목록 엑셀 내려받기 API가 없어 막아 둔다
      actions={
        <Button variant="secondary" disabled title="엑셀 내려받기는 준비 중이에요">
          엑셀로 내려받기
        </Button>
      }
    >
      <div role="group" aria-label="키트 배송 건수" className="flex gap-4">
        {summary.data ? (
          <>
            <StatTile label="배송 준비" value={String(summary.data.preparing ?? 0)} unit="건" />
            <StatTile label="배송 중" value={String(summary.data.shipping ?? 0)} unit="건" />
            <StatTile label="배송 완료" value={String(summary.data.delivered ?? 0)} unit="건" />
            <StatTile
              label="환불 기한 전"
              value={String(summary.data.beforeRefundDeadline ?? 0)}
              unit="건"
            />
          </>
        ) : summary.isError ? (
          <Alert
            tone="danger"
            title="배송 건수를 불러오지 못했어요"
            description={kitErrorMessage(summary.error)}
          />
        ) : (
          Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-24 flex-1" />)
        )}
      </div>

      <ConsoleCard>
        <div className="flex items-end gap-4">
          <Select
            label="대회"
            items={raceItems}
            value={raceDraft}
            onValueChange={(value) => setRaceDraft(value ?? ALL)}
            className="flex-1"
          />
          <Select
            label="상태"
            items={STATUS_ITEMS}
            value={statusDraft}
            onValueChange={(value) => setStatusDraft(value ?? ALL)}
            className="flex-1"
          />
          <Button variant="secondary" onClick={search}>
            조회
          </Button>
        </div>
      </ConsoleCard>

      <div className="flex flex-wrap items-start gap-6">
        <ConsoleCard
          title={`주문 ${kits.data?.totalElements ?? 0}건`}
          aside={
            <button
              type="button"
              className={cn('rounded-sm text-label-m text-text-brand', FOCUS_RING)}
            >
              주문번호로 찾기
            </button>
          }
          className="min-w-130 flex-1"
        >
          {kits.isError ? (
            <Alert
              tone="danger"
              title="키트 주문을 불러오지 못했어요"
              description={kitErrorMessage(kits.error)}
            />
          ) : !kits.data ? (
            <Skeleton className="h-40 w-full" />
          ) : rows.length === 0 ? (
            <EmptyState
              title="키트 주문이 없어요"
              description="결제가 끝난 키트 주문이 생기면 여기에 보여요"
            />
          ) : (
            <DataTable
              label="키트 주문"
              rows={rows}
              rowKey={(k) => String(kitId(k))}
              selected={checked.map(String)}
              columns={[
                {
                  key: 'check',
                  header: <span className="sr-only">고르기</span>,
                  width: 'w-12',
                  render: (k) => (
                    <Checkbox
                      aria-label={`${k.orderNo} 고르기`}
                      checked={checked.includes(kitId(k))}
                      onCheckedChange={(on) => toggle(kitId(k), on)}
                    />
                  ),
                },
                { key: 'id', header: '주문번호', width: 'w-40', render: (k) => k.orderNo },
                {
                  key: 'buyer',
                  header: '구매자',
                  width: 'w-22.5',
                  // 구매자 이름 · 배송지는 Clarity 화면 녹화에서 가린다
                  render: (k) => <span data-clarity-mask="true">{k.participantName}</span>,
                },
                { key: 'option', header: '키트 옵션', render: kitOption },
                {
                  key: 'status',
                  header: '상태',
                  width: 'w-25',
                  render: (k) => {
                    const status = KIT_STATUS[k.kitDeliveryStatus ?? 'PREPARING']
                    return <Badge tone={status.tone}>{status.label}</Badge>
                  },
                },
                {
                  key: 'address',
                  header: '배송지',
                  width: 'w-27.5',
                  render: (k) => (
                    <span data-clarity-mask="true">{shortAddress(k.shippingAddress?.address)}</span>
                  ),
                },
              ]}
            />
          )}
          <p className="text-body-s text-text-tertiary">
            구매자 실명·연락처는 배송 처리에 필요한 범위에서만 보여요.
          </p>
        </ConsoleCard>

        <ConsoleCard title="배송 상태 바꾸기" className="w-90 shrink-0">
          <p className="text-body-s text-text-secondary">
            {selected.length}건 선택
            {selectedLabels.length > 0 && ` · ${selectedLabels.join(' · ')}`}
          </p>
          <div className="flex items-center gap-2">
            {KIT_FLOW.map((step, i) => (
              <Fragment key={step}>
                {i > 0 && <span className="text-label-m text-text-tertiary">→</span>}
                <Chip
                  selected={step === next}
                  disabled={step === 'PREPARING'}
                  onClick={() => setNext(step as NextStatus)}
                >
                  {KIT_STATUS[step].label}
                </Chip>
              </Fragment>
            ))}
          </div>
          {next === 'SHIPPING' && (
            <>
              <Select
                label="택배사"
                items={CARRIERS}
                value={carrier}
                onValueChange={(value) => setCarrier(value ?? CARRIERS[0].value)}
              />
              {selected.length > 1 ? (
                <Textarea
                  label="송장 번호"
                  value={tracking}
                  onChange={(event) => setTracking(event.target.value)}
                  helper="여러 건을 고르면 줄마다 따로 넣어요"
                />
              ) : (
                <TextField
                  label="송장 번호"
                  value={tracking}
                  onChange={(event) => setTracking(event.target.value)}
                  helper="여러 건을 고르면 줄마다 따로 넣어요"
                />
              )}
              {selected.length > 1 && (
                // 줄은 표의 위에서부터 순서대로 주문에 붙는다. 한 번 보내면 되돌릴 수 없어서 짝을 미리 보여 준다.
                <ul aria-label="발송 미리보기" className="flex flex-col gap-1 text-body-s">
                  {selected.map((kit, i) => (
                    <li key={kitId(kit)} className="flex gap-2 text-text-secondary">
                      <span>{kit.orderNo}</span>
                      <span aria-hidden>→</span>
                      <span className="text-text-primary">{lines[i] ?? ''}</span>
                    </li>
                  ))}
                </ul>
              )}
            </>
          )}
          <p className="text-body-s text-text-tertiary">
            키트 배송은 정산과 관계없어요. 정산은 환불 기한과 대회 일정으로 정해져요.
          </p>
          {blocker && <p className="text-body-s text-text-secondary">{blocker}</p>}
          {result && result.done > 0 && (
            <Alert tone="success" title={`${result.done}건을 ${TO[next]} 바꿨어요`} />
          )}
          {result && result.failures.length > 0 && (
            <Alert
              tone="danger"
              title={`${result.failures.length}건은 바꾸지 못했어요`}
              description={result.failures
                .map((f) => `${orderNoOf(f.orderItemId)}: ${kitErrorMessage(f.error)}`)
                .join(' / ')}
            />
          )}
          <Button className="w-full" disabled={!canSubmit} onClick={submit}>
            {TO[next]} 바꾸기
          </Button>
        </ConsoleCard>
      </div>
    </ConsolePage>
  )
}
