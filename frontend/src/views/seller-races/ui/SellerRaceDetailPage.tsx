'use client'

import { useQuery } from '@tanstack/react-query'
import type { Route } from 'next'
import Link from 'next/link'
import { useState, type ReactNode } from 'react'

import { ConsoleCard, ConsoleLoading, ConsolePage } from '@/widgets/console-page'
import { RaceSaleActionButton } from '@/features/race-sale-status'
import { useToggleSale } from '@/features/seller-race'
import {
  formatDDay,
  formatRaceDate,
  formatRaceDateTime,
  isRaceEditable,
  myRacesQueryOptions,
  raceForEditQueryOptions,
  SaleRateBar,
  SELLER_SALE_STATUS_TEXT,
  toSellerRaceRow,
  type RaceForEdit,
} from '@/entities/race'
import { ApiError, productApi, unwrapData, type productModel } from '@/shared/api'
import { cn, formatNumber } from '@/shared/lib'
import {
  Alert,
  buttonClassName,
  Dialog,
  EmptyState,
  FOCUS_RING,
  Icon,
  KeyValue,
  StatStrip,
  StatTile,
  TableCell,
  TableHeaderCell,
  useToast,
} from '@/shared/ui'

import { coverUrl, initialImageChange } from '../model/race-image-change'

const n = formatNumber
const KEY = '[&>span:first-child]:w-30'

// 피그마 V4 · 판매자 · 대회 상세: 상태 → 대회명 → 대회일·장소, 판매 요약, 종목별 판매 + 바로 가기,
// 기본 정보 · 판매 일정 · 이미지 · 종목·참가권 · 취소·환불 · 키트
export function SellerRaceDetailPage({ raceId }: { raceId: number }) {
  const races = useQuery(myRacesQueryOptions())
  const detail = useQuery(raceForEditQueryOptions(raceId))
  const race = races.data?.find((r) => r.raceId === raceId)
  const row = race && toSellerRaceRow(race)
  const [stopOpen, setStopOpen] = useState(false)
  const toast = useToast()
  const stop = useToggleSale(raceId, 'stop')
  const resume = useToggleSale(raceId, 'resume')

  const saleAction = (mutation: typeof stop, done: string) =>
    mutation.mutate(undefined, {
      onSuccess: () => {
        setStopOpen(false)
        toast({ message: done, tone: 'success' })
      },
      onError: (error) =>
        toast({
          message:
            (error instanceof ApiError && error.serverMessage) || '잠시 뒤 다시 시도해 주세요.',
          tone: 'error',
        }),
    })

  const full = detail.data?.race
  const meta = [
    full?.raceDate ? formatRaceDateTime(full.raceDate) : row?.date,
    full?.location,
  ].filter(Boolean)

  return (
    <ConsolePage
      title={row?.title ?? '대회 상세'}
      description={row ? meta.join(' · ') : undefined}
      status={
        row && (
          <span className={row.saleStatus ? SELLER_SALE_STATUS_TEXT[row.saleStatus] : ''}>
            {row.badge.label}
          </span>
        )
      }
      actions={
        row && (
          <>
            {isRaceEditable(row.saleStatus) && (
              <Link
                href={`/seller/races/${raceId}/change` as Route}
                className={buttonClassName({ variant: 'secondary', size: 'm' })}
              >
                일정 변경 · 대회 취소
              </Link>
            )}
            {row.saleStatus === 'SCHEDULED' && (
              <RaceSaleActionButton raceId={raceId} saleStatus={row.saleStatus} by="seller" />
            )}
            {row.saleStatus === 'ON_SALE' && (
              <button
                type="button"
                className={buttonClassName({ variant: 'secondary', size: 'm' })}
                onClick={() => setStopOpen(true)}
              >
                판매 멈추기
              </button>
            )}
            {row.saleStatus === 'STOPPED' && (
              <button
                type="button"
                className={buttonClassName({ variant: 'secondary', size: 'm' })}
                disabled={resume.isPending}
                onClick={() => saleAction(resume, '판매를 다시 열었어요')}
              >
                다시 열기
              </button>
            )}
            {isRaceEditable(row.saleStatus) && (
              <Link
                href={`/seller/races/${raceId}/edit` as Route}
                className={buttonClassName({ variant: 'primary', size: 'm' })}
              >
                {row.saleStatus === 'SCHEDULED' ? '대회 수정하기' : '판매 중 변경'}
              </Link>
            )}
          </>
        )
      }
    >
      {races.isPending ? (
        <ConsoleLoading />
      ) : races.isError ? (
        <Alert
          tone="danger"
          title="대회 정보를 불러오지 못했어요"
          description={
            (races.error instanceof ApiError && races.error.serverMessage) ||
            '잠시 후 다시 시도해 주세요.'
          }
        />
      ) : !race || !row ? (
        <EmptyState title="찾을 수 없는 대회예요" />
      ) : (
        <>
          {row.saleStatus !== 'SCHEDULED' && <SaleSummary race={race} />}
          <div className="max-lg:grid-cols-1 grid grid-cols-[minmax(0,2fr)_minmax(0,1fr)] gap-6">
            {row.saleStatus !== 'SCHEDULED' && <CourseSales race={race} />}
            <ConsoleCard title="바로 가기" className="self-start">
              <nav aria-label="바로 가기" className="flex flex-col gap-2">
                {row.saleStatus === 'SCHEDULED' && (
                  <ShortcutRow
                    href="/seller/races"
                    label="판매 시작"
                    hint={race.saleOpenDate ? formatRaceDate(race.saleOpenDate) : undefined}
                  />
                )}
                <ShortcutRow
                  href={`/seller/sales?race=${raceId}` as Route}
                  label="판매 현황"
                  hint={`참가자 ${n(row.sold)}명`}
                />
                <ShortcutRow href={`/seller/kits?race=${raceId}` as Route} label="키트 배송" />
                <ShortcutRow href="/seller/settlements" label="정산" />
              </nav>
            </ConsoleCard>
          </div>
          {detail.isError ? (
            <Alert
              tone="danger"
              title="대회 정보를 불러오지 못했어요"
              description="잠시 후 다시 시도해 주세요."
            />
          ) : detail.data ? (
            <RaceInfoSections race={detail.data.race} edit={detail.data} />
          ) : (
            <ConsoleLoading />
          )}
          <Dialog
            open={stopOpen}
            onOpenChange={setStopOpen}
            title="판매를 멈출까요?"
            body="새로 참가할 수 없어요. 이미 잡은 결제 대기는 기한 안에 결제할 수 있어요."
            confirmLabel="판매 멈추기"
            confirming={stop.isPending}
            onConfirm={() => saleAction(stop, '판매를 멈췄어요')}
          />
        </>
      )}
    </ConsolePage>
  )
}

function SaleSummary({ race }: { race: productModel.SellerRaceDto }) {
  const courses = race.courses ?? []
  const sum = (pick: (c: productModel.SellerRaceCourseDto) => number | undefined) =>
    courses.reduce((total, c) => total + (pick(c) ?? 0), 0)
  const sold = sum((c) => c.sold)
  const quota = sum((c) => c.quota)
  const reserved = sum((c) => c.reserved)
  return (
    <StatStrip>
      <StatTile layout="cell" label="판매" value={n(sold)} unit={`/ ${n(quota)}명`} />
      <StatTile layout="cell" label="결제 대기" value={n(reserved)} unit="명" />
      <StatTile
        layout="cell"
        label="남은 자리"
        value={n(Math.max(0, quota - sold - reserved))}
        unit="자리"
      />
      <StatTile
        layout="cell"
        label="접수 마감"
        value={race.applicationCloseAt ? formatDDay(race.applicationCloseAt) : '-'}
      />
    </StatStrip>
  )
}

function CourseSales({ race }: { race: productModel.SellerRaceDto }) {
  return (
    <ConsoleCard title="종목별 판매">
      <table aria-label="종목별 판매" className="w-full table-fixed border-collapse">
        <thead>
          <tr>
            <TableHeaderCell>종목</TableHeaderCell>
            <TableHeaderCell className="w-72 max-w-none">판매 / 정원</TableHeaderCell>
            <TableHeaderCell align="right" className="w-28 max-w-none">
              남은 자리
            </TableHeaderCell>
          </tr>
        </thead>
        <tbody>
          {(race.courses ?? []).map((course) => {
            const sold = course.sold ?? 0
            const quota = course.quota ?? 0
            const reserved = course.reserved ?? 0
            return (
              <tr key={course.raceCourseId}>
                <TableCell className="text-body1-medium">{course.name}</TableCell>
                <TableCell>
                  <SaleRateBar
                    aria-label={`${course.name} 판매율`}
                    percent={quota ? (sold / quota) * 100 : 0}
                    label={`${n(sold)} / ${n(quota)}`}
                  />
                </TableCell>
                <TableCell align="right" className="tabular-nums">
                  {n(Math.max(0, quota - sold - reserved))}
                </TableCell>
              </tr>
            )
          })}
        </tbody>
      </table>
    </ConsoleCard>
  )
}

function KitSection({ raceId }: { raceId: number }) {
  const kits = useQuery({
    queryKey: productApi.getFindKitOptionsQueryKey(raceId),
    queryFn: async () => unwrapData(await productApi.findKitOptions(raceId)),
  })
  const groups = kits.data?.kitOptions ?? []
  if (kits.isPending) {
    return (
      <ConsoleCard title="키트">
        <ConsoleLoading />
      </ConsoleCard>
    )
  }
  if (kits.isError) {
    return (
      <ConsoleCard title="키트">
        <Alert
          tone="danger"
          title="키트를 불러오지 못했어요"
          description="잠시 후 다시 시도해 주세요."
        />
      </ConsoleCard>
    )
  }
  return (
    <ConsoleCard title="키트">
      <table aria-label="키트" className="w-full table-fixed border-collapse">
        <thead>
          <tr>
            <TableHeaderCell className="w-60 max-w-none">옵션 이름</TableHeaderCell>
            <TableHeaderCell>옵션 값</TableHeaderCell>
            <TableHeaderCell align="right" className="w-40 max-w-none">
              준비 수량
            </TableHeaderCell>
          </tr>
        </thead>
        <tbody>
          {groups.map((group) => {
            const stocks = group.values.map((v) => v.stock)
            return (
              <tr key={group.name}>
                <TableCell className="text-body1-medium">{group.name}</TableCell>
                <TableCell>{group.values.map((v) => v.value).join(' · ')}</TableCell>
                <TableCell align="right" className="tabular-nums">
                  {stocks.some((s) => s == null)
                    ? '맞춤 제작'
                    : `${n(stocks.reduce((t: number, s) => t + (s ?? 0), 0))}개`}
                </TableCell>
              </tr>
            )
          })}
        </tbody>
      </table>
    </ConsoleCard>
  )
}

function RaceInfoSections({ race, edit }: { race: productModel.RaceDetailDto; edit: RaceForEdit }) {
  const intro = initialImageChange(edit).intro
  const courses = race.courses ?? []
  const kitCourses = courses.filter((c) => c.kitIncluded)
  const dateTime = (value?: string | null) => (value ? fullDateTime(value) : '-')
  return (
    <>
      <ConsoleCard title="기본 정보">
        <div className="flex flex-col gap-4">
          <KeyValue className={KEY} label="대회 이름" value={race.title} />
          <KeyValue className={KEY} label="대회일 · 출발" value={dateTime(race.raceDate)} />
          <KeyValue className={KEY} label="장소" value={race.location} />
          <KeyValue className={KEY} label="대회 소개" value={race.description || '-'} />
        </div>
      </ConsoleCard>
      <ConsoleCard title="판매 일정">
        <div className="flex flex-col gap-4">
          {race.crewSaleStartAt && (
            <KeyValue
              className={KEY}
              label="크루 선예매"
              value={`${dateTime(race.crewSaleStartAt)} ~ ${dateTime(race.crewSaleEndAt)}`}
            />
          )}
          <KeyValue
            className={KEY}
            label="일반 판매"
            value={race.individualOpenAt ? `${dateTime(race.individualOpenAt)}부터` : '-'}
          />
          <KeyValue className={KEY} label="접수 마감" value={dateTime(race.applicationCloseAt)} />
        </div>
      </ConsoleCard>
      <ConsoleCard title="이미지">
        <div className="flex flex-wrap gap-4" aria-label="대표 이미지와 소개 이미지">
          <ImageTile label="대표 이미지" url={coverUrl(edit)} />
          {intro.length === 0 ? (
            <ImageTile label="소개 이미지" />
          ) : (
            intro.map((slot, i) => (
              <ImageTile
                key={slot.key}
                label={i === 0 ? `소개 이미지 ${intro.length}장` : ''}
                alt={`소개 이미지 ${i + 1}`}
                url={slot.url}
              />
            ))
          )}
        </div>
      </ConsoleCard>
      <ConsoleCard title="종목 · 참가권">
        <table aria-label="종목 · 참가권" className="w-full table-fixed border-collapse">
          <thead>
            <tr>
              <TableHeaderCell>종목</TableHeaderCell>
              <TableHeaderCell align="right">거리</TableHeaderCell>
              <TableHeaderCell align="right">참가비</TableHeaderCell>
              <TableHeaderCell align="right">크루 정원</TableHeaderCell>
              <TableHeaderCell align="right">일반 정원</TableHeaderCell>
              <TableHeaderCell>최소 나이</TableHeaderCell>
              <TableHeaderCell>키트</TableHeaderCell>
            </tr>
          </thead>
          <tbody>
            {courses.map((c) => (
              <tr key={c.raceCourseId}>
                <TableCell className="text-body1-medium">{c.name}</TableCell>
                <TableCell align="right">{c.distanceKm}km</TableCell>
                <TableCell align="right">{n(c.price ?? 0)}원</TableCell>
                <TableCell align="right">{n(c.crewQuota ?? 0)}명</TableCell>
                <TableCell align="right">{n(c.individualQuota ?? 0)}명</TableCell>
                <TableCell>만 {c.minAge}세 이상</TableCell>
                <TableCell>{c.kitIncluded ? '포함' : '없음'}</TableCell>
              </tr>
            ))}
          </tbody>
        </table>
      </ConsoleCard>
      <ConsoleCard title="취소 · 환불">
        <div className="flex flex-col gap-4">
          <KeyValue
            className={KEY}
            label="환불 기한"
            value={race.refundDeadline ? `${formatRaceDate(race.refundDeadline)} 23:59까지` : '-'}
          />
          <KeyValue
            className={KEY}
            label="그 뒤 취소"
            value={<span className="text-text-danger">환불 불가</span>}
          />
        </div>
      </ConsoleCard>
      {kitCourses.length > 0 && <KitSection raceId={race.raceId ?? 0} />}
    </>
  )
}

// 2026-11-29T08:00:00 → 2026-11-29 (일) 08:00. 00:00이면 날짜만
function fullDateTime(value: string): string {
  const short = formatRaceDateTime(value)
  return `${value.slice(0, 10)} ${short.slice(short.indexOf('('))}`
}

function ImageTile({
  label,
  alt = label,
  url,
}: {
  label: string
  alt?: string
  url?: string
}): ReactNode {
  return (
    <div className="flex flex-col gap-2">
      {/* 둘째 소개 이미지부터는 이름 자리만 비워 사진 높이를 맞춘다 */}
      <span className="min-h-4 text-caption-regular text-text-tertiary">{label}</span>
      <div className="flex size-32 items-center justify-center overflow-hidden rounded-md bg-bg-subtle">
        {url ? (
          // 만료되는 S3 주소라 next/image로 최적화하지 않는다
          // eslint-disable-next-line @next/next/no-img-element
          <img src={url} alt={alt} className="size-full object-cover" />
        ) : (
          <Icon name="Image" className="size-6 text-text-tertiary" aria-hidden />
        )}
      </div>
    </div>
  )
}

function ShortcutRow({
  href,
  label,
  hint,
}: {
  href: Route | string
  label: string
  hint?: string
}) {
  return (
    <Link
      href={href as Route}
      className={cn(
        'flex h-12 items-center justify-between rounded-md bg-bg-surface px-4 text-body2-medium shadow-sm inset-ring inset-ring-border-default',
        FOCUS_RING,
      )}
    >
      {label}
      {hint && <span className="text-caption-regular text-text-tertiary">{hint}</span>}
    </Link>
  )
}
