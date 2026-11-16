'use client'

import { useQuery } from '@tanstack/react-query'
import type { Route } from 'next'
import Link from 'next/link'
import type { ReactNode } from 'react'

import { AxBriefing } from '@/widgets/ax-briefing'
import { ConsoleCard, ConsoleLoading, ConsoleOffline, ConsolePage } from '@/widgets/console-page'
import { myRacesQueryOptions } from '@/entities/race'
import {
  KIT_STATUS,
  shippableCount,
  shortAddress,
  useSellerKits,
  useSellerKitSummary,
  type KitStatus,
} from '@/entities/seller-kit'
import { ApiError, payoutApi, unwrapData, type productModel } from '@/shared/api'
import { cn, formatNumber, useNow, useSearchParamsState } from '@/shared/lib'
import {
  Alert,
  Badge,
  buttonClassName,
  EmptyState,
  FOCUS_RING,
  Icon,
  Segmented,
  Select,
  Skeleton,
  type IconName,
} from '@/shared/ui'

import { courseSales, formatBaseDate, formatKoreanMonthDay, soldRate } from '../lib/dashboard'

function errorText(error: unknown) {
  return (error instanceof ApiError && error.serverMessage) || '잠시 뒤 다시 시도해 주세요.'
}

// 대시보드 카드: 흰 바탕 · 얇은 테두리 (시안 「관리자 대시보드와 같은 규칙」).
// 시안 둥글기 24는 토큰에 없어 ConsoleCard의 16을 그대로 쓴다
const CARD = 'border border-border-default'

/** 표에 보여 줄 키트 주문 수. 전체는 주문 관리(키트 배송) 화면에서 본다 */
const KIT_ROWS = 5

// 피그마 판매자 센터 · 대시보드 B안 (1388:60975) + 판매자 · 관리자 공통 로딩 · 연결 끊김
export function SellerDashboardPage({ state }: { state?: 'loading' | 'offline' }) {
  const now = useNow()
  return (
    <ConsolePage
      title="대시보드"
      description={now ? formatBaseDate(now) : undefined}
      actions={
        <Link href="/seller/races/new?from=home" className={buttonClassName()}>
          대회 등록하기
        </Link>
      }
    >
      {state === 'loading' && <ConsoleLoading />}
      {state === 'offline' && <ConsoleOffline />}
      {!state && <DashboardBody />}
    </ConsolePage>
  )
}

// 아직 데이터가 없고, 에러가 났거나 재시도 중이면 실패로 본다
function isFailing(query: { data: unknown; isError: boolean; failureCount: number }) {
  return query.data === undefined && (query.isError || query.failureCount > 0)
}

function DashboardBody() {
  const races = useQuery(myRacesQueryOptions())

  return (
    <>
      <AxBriefing screen="seller" askHref="/seller/ai" basisHref="/seller/settlements" />
      <div className="flex items-stretch gap-6">
        <TodoCard />
        <PayoutCard races={races.data} racesFailed={races.isError} />
      </div>
      <KitCard races={races.data} />
      <CourseSalesCard races={races.data} error={races.isError ? races.error : null} />
    </>
  )
}

// 처리할 일 = 지금 보낼 수 있는 키트 + 지급보류 (11/4 결정). 아래 탭의 「송장 입력 대기」는 환불 기한 전 키트도 세서 이름을 다르게 둔다
function TodoCard() {
  const kits = useSellerKitSummary()
  const summary = payoutApi.useSummary({ query: { select: unwrapData } })
  const shippable = kits.data && shippableCount(kits.data)
  const held = summary.data?.heldCount
  const total = shippable !== undefined && held !== undefined ? shippable + held : undefined
  // 재시도 중(failureCount > 0)이어도 첫 실패부터 「-」를 보여 준다. 재시도가 성공하면 숫자로 바뀐다
  const failed = isFailing(kits) || isFailing(summary)

  return (
    <ConsoleCard
      title={`처리할 일${total !== undefined ? ` · ${total}건` : failed ? ' · -' : ''}`}
      className={cn(CARD, 'flex-1 gap-2')}
    >
      <TodoRow
        href="/seller/kits"
        icon="Truck"
        iconClass="bg-bg-badge-warning"
        label={countLabel('지금 보낼 수 있는 키트', shippable, isFailing(kits))}
        menu="주문 관리"
      />
      <TodoRow
        href="/seller/settlements"
        icon="TriangleAlert"
        iconClass="bg-bg-badge-danger"
        label={countLabel('지급보류', held, isFailing(summary))}
        menu="정산 관리"
      />
    </ConsoleCard>
  )
}

function countLabel(name: string, count: number | undefined, failed: boolean): ReactNode {
  if (count !== undefined) return `${name} ${formatNumber(count)}건`
  if (failed) return `${name} -`
  return <Skeleton className="h-5 w-32" />
}

function TodoRow({
  href,
  icon,
  iconClass,
  label,
  menu,
}: {
  href: Route
  icon: IconName
  iconClass: string
  label: ReactNode
  menu: string
}) {
  return (
    <Link href={href} className={cn('flex items-center gap-3 rounded-sm py-2', FOCUS_RING)}>
      <span
        className={cn('flex size-8 shrink-0 items-center justify-center rounded-full', iconClass)}
      >
        <Icon name={icon} className="size-5" />
      </span>
      <span className="min-w-0 flex-1 text-body2-medium">{label}</span>
      <span className="text-body2-regular text-text-secondary">{menu}</span>
      <Icon name="ChevronRight" className="size-4" />
    </Link>
  )
}

function PayoutCard({
  races,
  racesFailed,
}: {
  races: productModel.SellerRaceDto[] | undefined
  racesFailed: boolean
}) {
  const summary = payoutApi.useSummary({ query: { select: unwrapData } })
  const courses = races?.flatMap((race) => race.courses ?? [])
  const sold = courses?.reduce((sum, c) => sum + (c.sold ?? 0), 0)
  const capacity = courses?.reduce((sum, c) => sum + (c.quota ?? 0), 0)

  return (
    <ConsoleCard
      title="다음 정산"
      aside={
        <Link
          href="/seller/settlements"
          className={buttonClassName({ variant: 'secondary', size: 's' })}
        >
          정산 관리
        </Link>
      }
      className={cn(CARD, 'flex-1')}
    >
      {summary.data ? (
        <>
          <p className="flex items-baseline gap-2">
            <span className="font-display text-number-s">
              {formatNumber(summary.data.scheduledPayoutAmount)}
            </span>
            <span className="text-body2-regular text-text-secondary">원</span>
          </p>
          <div className="flex items-center gap-2">
            {/* 지급 예정 정산이 없으면 다음 정산일이 null이다 */}
            <p className="text-body2-regular text-text-secondary">
              {summary.data.nextPayDate
                ? `${formatKoreanMonthDay(summary.data.nextPayDate)} 지급 예정`
                : '지급 예정 정산 없음'}
            </p>
            {summary.data.heldCount > 0 && (
              <Badge tone="warning">지급보류 {summary.data.heldCount}건</Badge>
            )}
          </div>
        </>
      ) : summary.isError ? (
        <Alert
          tone="danger"
          title="정산 요약을 불러오지 못했어요"
          description={errorText(summary.error)}
        />
      ) : (
        <Skeleton className="h-16 w-full" />
      )}
      <div className="h-px bg-border-default" />
      <div className="flex flex-col gap-1">
        <p className="text-body2-regular text-text-secondary">판매한 참가권</p>
        {sold !== undefined && capacity !== undefined ? (
          <>
            <p className="flex items-baseline gap-1">
              <span className="font-display text-title2">{formatNumber(sold)}</span>
              <span className="text-body2-regular text-text-secondary">장</span>
            </p>
            <p className="text-caption-regular text-text-tertiary">
              정원의 {soldRate(sold, capacity)}%
            </p>
          </>
        ) : racesFailed ? (
          <p className="text-title2">-</p>
        ) : (
          <Skeleton className="h-12 w-32" />
        )}
      </div>
    </ConsoleCard>
  )
}

// 송장 입력 대기 = 배송 준비. 환불 기한 전 키트도 섞여 있어 실제로 보낼 수 있는지는 키트 배송 화면에서 가린다
const KIT_TABS: KitStatus[] = ['PREPARING', 'SHIPPING', 'DELIVERED']
const KIT_TAB_LABEL: Record<KitStatus, string> = {
  PREPARING: '송장 입력 대기',
  SHIPPING: '배송 중',
  DELIVERED: '배송 완료',
}

function KitCard({ races }: { races: productModel.SellerRaceDto[] | undefined }) {
  const [url, setUrl] = useSearchParamsState({ kit: 'PREPARING' }, { kit: KIT_TABS })
  const status = url.kit as KitStatus
  const summary = useSellerKitSummary()
  const kits = useSellerKits({ status })
  const total = summary.data
    ? (summary.data.preparing ?? 0) + (summary.data.shipping ?? 0) + (summary.data.delivered ?? 0)
    : undefined
  const raceTitle = (raceId?: number) => races?.find((race) => race.raceId === raceId)?.title ?? ''
  const rows = kits.data && (kits.data.kits ?? []).slice(0, KIT_ROWS)

  return (
    <ConsoleCard
      title={`키트 배송 현황${total !== undefined ? ` · 전체 키트 ${formatNumber(total)}개` : ''}`}
      aside={
        <Link href="/seller/kits" className={buttonClassName({ variant: 'secondary', size: 's' })}>
          주문 관리
        </Link>
      }
      className={CARD}
    >
      <Segmented
        label="배송 상태"
        layout="hug"
        className="self-start rounded-full"
        value={status}
        onChange={(kit) => setUrl({ kit })}
        items={KIT_TABS.map((value) => ({
          value,
          label: KIT_TAB_LABEL[value],
          // 송장 입력 대기만 건수를 붙인다 (시안: 배송 중 · 배송 완료는 숫자 없음).
          // 표가 배송 준비 전체를 보여 주므로 탭 숫자도 배송 준비 전체로 맞춘다
          count: value === 'PREPARING' ? summary.data?.preparing : undefined,
        }))}
      />
      {kits.isError ? (
        <Alert
          tone="danger"
          title="키트 주문을 불러오지 못했어요"
          description={errorText(kits.error)}
        />
      ) : !rows ? (
        <Skeleton className="h-52 w-full" />
      ) : rows.length === 0 ? (
        <EmptyState title={`${KIT_TAB_LABEL[status]} 키트가 없어요`} />
      ) : (
        <table aria-label="키트 배송 현황" className="w-full table-fixed text-body2-regular">
          <thead>
            <tr className="text-left text-caption-medium text-text-tertiary">
              <th className="w-44 rounded-l-sm bg-bg-subtle px-3 py-2 font-medium">주문번호</th>
              <th className="w-24 bg-bg-subtle px-3 py-2 font-medium">참가자</th>
              <th className="bg-bg-subtle px-3 py-2 font-medium">대회 · 코스</th>
              <th className="w-32 bg-bg-subtle px-3 py-2 font-medium">배송지</th>
              <th className="w-24 bg-bg-subtle px-3 py-2 font-medium">배송 상태</th>
              <th className="w-28 rounded-r-sm bg-bg-subtle px-3 py-2 text-right font-medium">
                동작
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((kit) => {
              const kitStatus = kit.kitDeliveryStatus ?? 'PREPARING'
              return (
                <tr key={kit.orderItemId} className="h-13 border-b border-border-default">
                  <td className="truncate px-3 text-text-secondary">{kit.orderNo}</td>
                  <td className="truncate px-3">{kit.participantName}</td>
                  <td className="truncate px-3 text-text-secondary">
                    {[raceTitle(kit.raceId), kit.courseName].filter(Boolean).join(' · ')}
                  </td>
                  <td className="truncate px-3 text-text-secondary">
                    {shortAddress(kit.shippingAddress?.address)}
                  </td>
                  <td className="px-3">
                    <Badge tone={KIT_STATUS[kitStatus].tone}>{KIT_STATUS[kitStatus].label}</Badge>
                  </td>
                  <td className="px-3 text-right">
                    {kitStatus === 'PREPARING' && (
                      <Link
                        href="/seller/kits"
                        className={buttonClassName({ variant: 'secondary', size: 's' })}
                      >
                        송장 입력
                      </Link>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      )}
    </ConsoleCard>
  )
}

function CourseSalesCard({
  races,
  error,
}: {
  races: productModel.SellerRaceDto[] | undefined
  error: unknown
}) {
  const [url, setUrl] = useSearchParamsState({ race: '' })
  const race = races?.find((r) => String(r.raceId) === url.race) ?? races?.[0]
  const sales = race && courseSales(race.courses ?? [])
  const top = sales?.bars.reduce<(typeof sales.bars)[number] | undefined>(
    (best, bar) => (!best || bar.sold > best.sold ? bar : best),
    undefined,
  )

  return (
    <ConsoleCard
      title="코스별 판매 현황"
      aside={
        races &&
        races.length > 0 && (
          <Select
            aria-label="대회"
            className="w-60"
            value={String(race?.raceId ?? '')}
            onValueChange={(value) => value && setUrl({ race: value })}
            items={races.map((r) => ({ value: String(r.raceId), label: r.title ?? '' }))}
          />
        )
      }
      className={cn(CARD, 'gap-5')}
    >
      {error ? (
        <Alert tone="danger" title="대회 목록을 불러오지 못했어요" description={errorText(error)} />
      ) : !races ? (
        <Skeleton className="h-72 w-full" />
      ) : !sales ? (
        <EmptyState title="등록한 대회가 없어요" />
      ) : (
        <>
          <div className="flex gap-10">
            <Figure value={`${formatNumber(sales.sold)}장`} label="판매" />
            <Figure value={`${formatNumber(sales.reserved)}장`} label="결제 대기" />
            <Figure value={`${formatNumber(sales.remaining)}자리`} label="남은 자리" />
          </div>
          <div className="flex gap-4 text-caption-medium text-text-secondary">
            <span className="flex items-center gap-2">
              <span className="size-3 rounded-sm bg-bg-brand" />
              판매
            </span>
            <span className="flex items-center gap-2">
              <span className="size-3 rounded-sm bg-bg-track" />
              정원
            </span>
          </div>
          <ul
            aria-label="코스별 판매"
            className="flex h-60 items-end gap-4 border-b border-border-default"
          >
            {sales.bars.map((bar) => (
              <li
                key={`${bar.id}-${bar.name}`}
                className="flex h-full flex-1 flex-col items-center justify-end gap-2"
              >
                <span className="text-caption-medium text-text-secondary">
                  {formatNumber(bar.sold)}/{formatNumber(bar.quota)}
                </span>
                <span
                  className="relative w-9 overflow-hidden rounded-full bg-bg-track"
                  // 막대 위 숫자 · 아래 코스 이름 자리를 남기려고 칸 높이의 70%까지만 쓴다
                  style={{ height: `${bar.quotaPct * 0.7}%` }}
                >
                  <span
                    className={cn(
                      'absolute inset-x-0 bottom-0 rounded-full',
                      bar === top ? 'bg-bg-brand' : 'bg-bg-brand/40',
                    )}
                    style={{ height: `${bar.fillPct}%` }}
                  />
                </span>
                <span className="pb-2 text-caption-regular text-text-secondary">{bar.name}</span>
              </li>
            ))}
          </ul>
        </>
      )}
    </ConsoleCard>
  )
}

function Figure({ value, label }: { value: string; label: string }) {
  return (
    <div className="flex flex-col gap-1">
      <p className="text-title2">{value}</p>
      <p className="text-body2-regular text-text-secondary">{label}</p>
    </div>
  )
}
