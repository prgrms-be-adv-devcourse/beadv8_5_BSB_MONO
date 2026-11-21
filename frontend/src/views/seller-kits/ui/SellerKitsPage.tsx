'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'

import { ConsolePage } from '@/widgets/console-page'
import { KitExportButton } from '@/features/seller-kit-export'
import { myRacesQueryOptions } from '@/entities/race'
import {
  KIT_FLOW,
  KIT_STATUS,
  kitErrorMessage,
  useCompleteKitDeliveries,
  useSellerKits,
  useSellerKitSummary,
  useShipKits,
  type KitFailure,
  type KitFilters,
  type KitStatus,
} from '@/entities/seller-kit'
import { orderModel } from '@/shared/api'
import { cn, useSearchParamsState } from '@/shared/lib'
import {
  Alert,
  Button,
  Checkbox,
  FOCUS_RING,
  Icon,
  Select,
  Skeleton,
  Tab,
  TabBar,
  Tabs,
  TextField,
} from '@/shared/ui'

import {
  CARRIERS,
  missingTracking,
  rowKey,
  shipFormSchema,
  TAB_LABEL,
  TRACKING_NOTICE,
  trackingOnly,
  type ShipFormValues,
} from '../model/ship-form'
import { KitOrderDetail } from './KitOrderDetail'

type Kit = orderModel.SellerKitDto

/** 발송 · 배송 완료로 바꿀 수 있는 다음 단계 (배송 준비로 되돌리는 API는 없다) */
type NextStatus = Exclude<KitStatus, 'PREPARING'>

const ALL = ''
// 탭: 송장 입력 대기(배송 준비) · 배송 중 · 배송 완료. 탭 이름 옆에 건수를 붙인다.
// 환불 기한 전 · 취소 탭은 목록 API가 없어 두지 않는다(환불 기한 전은 건수만 글자로 보여 준다)
const TAB_VALUES: string[] = [...KIT_FLOW]

/** 한 쪽에 보여 줄 줄 수. 목록은 한 번에 받아 화면 안에서 나눈다 */
const PAGE_ROWS = 10

// 버튼 문구의 조사 (배송 중으로 · 배송 완료로)
const TO: Record<KitStatus, string> = {
  PREPARING: '배송 준비로',
  SHIPPING: '배송 중으로',
  DELIVERED: '배송 완료로',
}

/** 그 단계로 바꾸려면 지금 이 단계여야 한다 */
const FROM: Record<NextStatus, KitStatus> = { SHIPPING: 'PREPARING', DELIVERED: 'SHIPPING' }

// 주문번호 · 이름 찾기: 칸 하나라 zod로 검사만 한다
const searchSchema = z.object({ q: z.string().trim().max(50) })

function kitId(kit: Kit) {
  return kit.orderItemId ?? 0
}

function kitOption(kit: Kit) {
  const options = kit.kitOptions?.map((o) => o.value).filter(Boolean) ?? []
  return (options.length > 0 ? options : [kit.tshirtSize && `티셔츠 ${kit.tshirtSize}`])
    .filter(Boolean)
    .join(' · ')
}

const TH = 'h-11 px-3 text-left text-caption-medium font-medium text-text-secondary'

// 피그마 Final v3 · 판매자 · 키트 배송: 대회 고르기 + 찾기, 카드 안에 탭 · 표 · 선택 막대
export function SellerKitsPage() {
  // 조회에 건 대회 · 상태 · 쪽은 URL에 담는다.
  const [url, setUrl] = useSearchParamsState(
    { race: ALL, status: 'PREPARING', page: '1' },
    { status: TAB_VALUES },
  )
  const raceId = Number(url.race)
  const filters: KitFilters = {
    raceId: url.race !== ALL && Number.isInteger(raceId) ? raceId : undefined,
    status: url.status as orderModel.FindKitsStatus,
  }
  const { control, register } = useForm<{ q: string }>({
    resolver: zodResolver(searchSchema),
    defaultValues: { q: '' },
  })
  const query = (useWatch({ control, name: 'q' }) ?? '').trim().toLowerCase()

  const races = useQuery(myRacesQueryOptions())
  const kits = useSellerKits(filters)
  const summary = useSellerKitSummary(filters.raceId)

  const [checked, setChecked] = useState<number[]>([])
  // 줄을 눌러 연 주문 상세 창
  const [openId, setOpenId] = useState<number | null>(null)
  // 바꿀 단계는 주소의 탭에서 정한다(?status=로 바로 들어와도 맞는다)
  const next: NextStatus = url.status === 'SHIPPING' ? 'DELIVERED' : 'SHIPPING'
  // 송장 입력 대기 줄마다 넣는 택배사 · 송장 번호
  const shipForm = useForm<ShipFormValues>({
    resolver: zodResolver(shipFormSchema),
    defaultValues: { carrier: {}, tracking: {} },
  })
  const tracking = useWatch({ control: shipForm.control, name: 'tracking' }) ?? {}
  const [result, setResult] = useState<{ done: number; failures: KitFailure[] } | null>(null)

  const ship = useShipKits()
  const deliver = useCompleteKitDeliveries()
  const pending = ship.isPending || deliver.isPending

  const all = kits.data?.kits ?? []
  const rows = query
    ? all.filter((kit) =>
        [kit.orderNo, kit.participantName].some((text) => text?.toLowerCase().includes(query)),
      )
    : all
  const totalPages = Math.max(1, Math.ceil(rows.length / PAGE_ROWS))
  const page = Math.min(Math.max(1, Math.trunc(Number(url.page)) || 1), totalPages)
  const pageRows = rows.slice((page - 1) * PAGE_ROWS, page * PAGE_ROWS)
  const selected = all.filter((kit) => checked.includes(kitId(kit)))
  const raceItems = [
    { value: ALL, label: '전체 대회' },
    ...(races.data ?? []).map((race) => ({ value: String(race.raceId), label: race.title ?? '' })),
  ]
  const raceTitle = (id?: number) => races.data?.find((r) => r.raceId === id)?.title ?? ''

  const toggle = (id: number, on: boolean) =>
    setChecked((prev) => (on ? [...prev, id] : prev.filter((key) => key !== id)))

  const changeFilter = (patch: { race?: string; status?: string; page?: string }) => {
    setUrl({ page: '1', ...patch })
    setChecked([])
    setOpenId(null)
    setResult(null)
  }

  const blocker =
    selected.length === 0
      ? null
      : selected.some((kit) => kit.kitDeliveryStatus !== FROM[next])
        ? `${KIT_STATUS[FROM[next]].label} 상태인 주문만 ${TO[next]} 바꿀 수 있어요.`
        : next === 'SHIPPING' && missingTracking(selected.map(kitId), tracking)
          ? '고른 주문마다 송장 번호를 넣어 주세요.'
          : null
  const canSubmit = selected.length > 0 && !blocker && !pending
  const preparing = url.status === 'PREPARING'

  async function submit() {
    setResult(null)
    const outcome =
      next === 'SHIPPING'
        ? await ship.mutateAsync(
            selected.map((kit) => ({
              orderItemId: kitId(kit),
              carrier: shipForm.getValues(`carrier.${rowKey(kitId(kit))}`) ?? CARRIERS[0].value,
              trackingNumber: (shipForm.getValues(`tracking.${rowKey(kitId(kit))}`) ?? '').trim(),
            })),
          )
        : await deliver.mutateAsync(selected.map((kit) => ({ orderItemId: kitId(kit) })))
    const failedIds = outcome.failures.map((f) => f.orderItemId)
    setResult({ done: outcome.total - failedIds.length, failures: outcome.failures })
    // 성공한 건은 고른 상태에서 빼고, 실패한 건만 남겨 다시 시도하게 한다
    setChecked((prev) => prev.filter((id) => failedIds.includes(id)))
    // 성공한 줄의 입력은 지우고, 실패한 줄은 그대로 둔다
    for (const kit of selected) {
      if (failedIds.includes(kitId(kit))) continue
      shipForm.resetField(`tracking.${rowKey(kitId(kit))}`, { defaultValue: '' })
    }
  }

  const openKit = all.find((kit) => kitId(kit) === openId)
  // 연 주문이 목록에서 빠지면(다른 곳에서 먼저 보냄 등) 창을 닫은 것으로 친다. 다시 보여도 저절로 열리지 않게
  if (openId !== null && kits.data && !openKit) setOpenId(null)

  const orderNoOf = (id: number) => all.find((kit) => kitId(kit) === id)?.orderNo ?? String(id)

  return (
    <ConsolePage title="키트 배송" actions={<KitExportButton filters={filters} />}>
      <div className="flex items-start gap-3">
        <Select
          aria-label="대회"
          items={raceItems}
          value={url.race}
          onValueChange={(value) => changeFilter({ race: value ?? ALL })}
          className="w-100"
        />
        <TextField
          compact
          leading={<Icon name="Search" />}
          aria-label="주문번호 · 이름으로 찾기"
          placeholder="주문번호 · 이름으로 찾기"
          className="w-80"
          {...register('q', { onChange: () => setUrl({ page: '1' }) })}
        />
      </div>

      <section
        aria-label="키트 주문"
        className="flex flex-col gap-4 rounded-md border border-border-default p-6"
      >
        {summary.isError && (
          <Alert
            tone="danger"
            title="배송 건수를 불러오지 못했어요"
            description={kitErrorMessage(summary.error)}
          />
        )}
        <Tabs
          value={url.status}
          onValueChange={(status) => changeFilter({ status: String(status) })}
        >
          <TabBar layout="hug" className="px-0">
            {KIT_FLOW.map((step) => (
              <Tab
                key={step}
                value={step}
                count={
                  summary.data
                    ? (summary.data[
                        step === 'PREPARING'
                          ? 'preparing'
                          : step === 'SHIPPING'
                            ? 'shipping'
                            : 'delivered'
                      ] ?? 0)
                    : undefined
                }
              >
                {TAB_LABEL[step]}
              </Tab>
            ))}
          </TabBar>
        </Tabs>
        {summary.data && (summary.data.beforeRefundDeadline ?? 0) > 0 && (
          // 아직 취소될 수 있어 보낼 수 없는 키트 (송장 입력 대기 안에 섞여 있다)
          <p className="text-body2-medium text-text-secondary">
            환불 기한 전 {summary.data.beforeRefundDeadline}건
          </p>
        )}

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

        {kits.isError ? (
          <Alert
            tone="danger"
            title="키트 주문을 불러오지 못했어요"
            description={kitErrorMessage(kits.error)}
          />
        ) : !kits.data ? (
          <Skeleton className="h-40 w-full" />
        ) : rows.length === 0 ? (
          // 시안 2070:68001: 카드 안에 글 한 줄
          <p className="py-13 text-center text-body1-regular text-text-secondary">
            {TAB_LABEL[url.status as KitStatus]} 키트가 없어요
          </p>
        ) : (
          <table aria-label="키트 주문" className="w-full table-fixed text-body1-regular">
            <thead>
              <tr className="border-b border-border-default">
                <th className={cn(TH, 'w-12')}>
                  {preparing ? (
                    <Checkbox
                      aria-label="이 쪽 전체 고르기"
                      checked={
                        pageRows.length > 0 && pageRows.every((kit) => checked.includes(kitId(kit)))
                      }
                      onCheckedChange={(on) =>
                        setChecked((prev) => [
                          ...prev.filter((id) => !pageRows.some((kit) => kitId(kit) === id)),
                          ...(on ? pageRows.map(kitId) : []),
                        ])
                      }
                    />
                  ) : (
                    <span className="sr-only">고르기</span>
                  )}
                </th>
                <th className={cn(TH, 'w-45')}>주문번호</th>
                <th className={cn(TH, 'w-26')}>참가자</th>
                <th className={TH}>대회 · 종목</th>
                <th className={cn(TH, 'w-55')}>키트 옵션</th>
                <th className={cn(TH, 'w-41')}>택배사</th>
                <th className={cn(TH, 'w-51')}>송장 번호</th>
              </tr>
            </thead>
            <tbody>
              {pageRows.map((kit) => {
                const id = kitId(kit)
                const editable = preparing && kit.kitDeliveryStatus === 'PREPARING'
                return (
                  <tr
                    key={id}
                    // 줄 어디를 눌러도 상세 창을 연다. 줄 안의 고르기 · 입력 칸을 누른 것은 빼고
                    onClick={(event) => {
                      if (!(event.target as HTMLElement).closest('button, input, label, [role]')) {
                        setOpenId(id)
                      }
                    }}
                    className="h-14 cursor-pointer border-b border-border-default last:border-b-0 hover:bg-bg-subtle"
                  >
                    <td className="px-3">
                      <Checkbox
                        aria-label={`${kit.orderNo} 고르기`}
                        checked={checked.includes(id)}
                        onCheckedChange={(on) => toggle(id, on)}
                      />
                    </td>
                    <td className="truncate px-3">
                      {/* 키보드로도 열 수 있게 주문번호를 버튼으로 둔다 */}
                      <button
                        type="button"
                        aria-label={`${kit.orderNo} 주문 상세`}
                        onClick={() => setOpenId(id)}
                        className={cn('max-w-full truncate rounded-sm text-left', FOCUS_RING)}
                      >
                        {kit.orderNo}
                      </button>
                    </td>
                    {/* 참가자 이름은 Clarity 화면 녹화에서 가린다 */}
                    <td className="truncate px-3" data-clarity-mask="true">
                      {kit.participantName}
                    </td>
                    <td className="truncate px-3">
                      {[raceTitle(kit.raceId), kit.courseName].filter(Boolean).join(' · ')}
                    </td>
                    <td className="truncate px-3">{kitOption(kit)}</td>
                    <td className="px-3">
                      {editable ? (
                        <Controller
                          control={shipForm.control}
                          name={`carrier.${rowKey(id)}`}
                          defaultValue={CARRIERS[0].value}
                          render={({ field }) => (
                            <Select
                              aria-label={`${kit.orderNo} 택배사`}
                              compact
                              items={CARRIERS}
                              value={field.value}
                              onValueChange={(value) => field.onChange(value ?? CARRIERS[0].value)}
                            />
                          )}
                        />
                      ) : (
                        kit.kitCarrier
                      )}
                    </td>
                    <td className="px-3">
                      {editable ? (
                        <TextField
                          compact
                          aria-label={`${kit.orderNo} 송장 번호`}
                          placeholder="송장 번호"
                          filter={trackingOnly}
                          filterMessage={TRACKING_NOTICE}
                          {...shipForm.register(`tracking.${rowKey(id)}`)}
                        />
                      ) : (
                        kit.kitTrackingNumber
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}

        {selected.length > 0 && url.status !== 'DELIVERED' && (
          // 하나 이상 고르면 표 아래에 뜨는 막대: 흰 바탕 + 1px 선 + 그림자
          <div
            role="region"
            aria-label="선택한 주문"
            className="flex items-center gap-3 rounded-md border border-border-default bg-bg-surface px-5 py-3 shadow-sm"
          >
            <p className="flex-1 text-body1-bold">{selected.length}건 선택</p>
            {blocker && <p className="text-body2-regular text-text-secondary">{blocker}</p>}
            <Button variant="ghost" onClick={() => setChecked([])}>
              선택 풀기
            </Button>
            <Button disabled={!canSubmit} loading={pending} onClick={submit}>
              {selected.length}건 {TO[next]} 바꾸기
            </Button>
          </div>
        )}

        {totalPages > 1 && (
          <nav aria-label="쪽" className="flex items-center justify-center gap-4">
            <button
              type="button"
              aria-label="이전 쪽"
              disabled={page <= 1}
              onClick={() => setUrl({ page: String(page - 1) })}
              className={cn('rounded-sm disabled:opacity-30', FOCUS_RING)}
            >
              <Icon name="ChevronLeft" className="size-4" />
            </button>
            <span className="text-body2-medium">
              {page} / {totalPages}
            </span>
            <button
              type="button"
              aria-label="다음 쪽"
              disabled={page >= totalPages}
              onClick={() => setUrl({ page: String(page + 1) })}
              className={cn('rounded-sm disabled:opacity-30', FOCUS_RING)}
            >
              <Icon name="ChevronRight" className="size-4" />
            </button>
          </nav>
        )}
      </section>

      <KitOrderDetail
        kit={openKit}
        raceTitle={raceTitle(openKit?.raceId)}
        kitOption={openKit ? kitOption(openKit) : ''}
        onClose={() => setOpenId(null)}
        onDone={() => {
          setOpenId(null)
          setResult({ done: 1, failures: [] })
        }}
      />
    </ConsolePage>
  )
}
