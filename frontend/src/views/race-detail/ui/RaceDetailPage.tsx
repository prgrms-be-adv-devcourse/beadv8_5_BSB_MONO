'use client'

import type { Route } from 'next'
import Image from 'next/image'
import Link from 'next/link'
import { useState, type ReactNode } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { RaceShareSheet } from '@/widgets/race-share-sheet'
import { useAuthStatus } from '@/features/auth'
import { useMyRaceStatus } from '@/entities/party'
import {
  formatRaceDateTime,
  parseKstDateTime,
  RacePoster,
  type RaceDetail,
  type RaceOption,
} from '@/entities/race'
import { cn, formatNumber, useNow, useTrackOnce } from '@/shared/lib'
import { Badge, buttonClassName, FOCUS_RING, Icon, IconButton, Section } from '@/shared/ui'

import { ApplyCta } from './ApplyCta'
import { SimilarRaces } from './SimilarRaces'

// 피그마 v3 대회 상세는 금액을 「35,000원」으로 쓴다
const formatPrice = (won: number) => `${formatNumber(won)}원`

const ANCHORS = [
  { value: 'options', label: '종목' },
  { value: 'schedule', label: '일정' },
  { value: 'refund', label: '환불' },
  { value: 'info', label: '상세' },
] as const
type Anchor = (typeof ANCHORS)[number]['value']

export type RaceDetailPageProps = {
  race: RaceDetail
  /** 처음 골라 둘 종목. 없거나 맞지 않으면 첫 종목 */
  optionId?: string
  /** M02-1: 이 대회에 이미 신청한 회원 (한 사람은 한 대회에 1건만) */
  alreadyApplied?: boolean
  /** M02-4: 파티 결제를 안 했거나 실패해 같은 대회에 다시 신청할 수 없는 회원 */
  reapplyBlocked?: boolean
}

// 피그마 V2 · M02 대회 상세 (+ M02-1 이미 신청함 · M02-3 크루 전용 자리 · M02-4 다시 신청할 수 없음 · M02-5 파티로 신청 중 · M02-6 비슷한 대회 · 공유 시트)
// 대회는 상품 API, 종목 이미지는 파일 API에서 받는다(page.tsx). 이미지가 없으면 색 포스터를 그린다.
export function RaceDetailPage({
  race,
  optionId: initialOptionId,
  alreadyApplied = false,
  reapplyBlocked = false,
}: RaceDetailPageProps) {
  const authStatus = useAuthStatus()
  useTrackOnce('view_item', {
    raceId: race.id,
    raceName: race.title,
    saleStatus: race.status.label,
  })
  const [optionId, setOptionId] = useState(
    race.options.find((o) => o.id === initialOptionId && !o.soldOut)?.id ??
      race.options.find((o) => !o.soldOut)?.id ??
      '',
  )
  // 크루 전용 자리가 있는 대회는 로그인한 뒤 내 파티 상태를 받아 신청 방법을 정한다
  const { data: party } = useMyRaceStatus(
    authStatus === 'authenticated' && race.crew ? Number(race.id) : undefined,
  )
  const inParty = party !== undefined && party.role !== 'NONE'
  const joinedAsMember = party?.role === 'MEMBER' && party.partyStatus !== 'PAID'
  const [shareOpen, setShareOpen] = useState(false)
  const refundDeadline = race.schedule.find((row) => row.label === '전액 환불 기한')?.value ?? '-'
  const [anchor, setAnchor] = useState<Anchor>('options')
  const hasInfo = !!race.description || race.images.length > 0
  const anchors = ANCHORS.filter((item) => item.value !== 'info' || hasInfo)
  const minPrice = race.options.length > 0 ? Math.min(...race.options.map((o) => o.price)) : 0

  function jumpTo(next: Anchor) {
    setAnchor(next)
    document.getElementById(`race-${next}`)?.scrollIntoView({ behavior: 'smooth' })
  }

  return (
    <>
      <AppHeader
        type="sub"
        title={race.title}
        action={
          <IconButton aria-label="공유하기" onClick={() => setShareOpen(true)}>
            <Icon name="Share" />
          </IconButton>
        }
      />
      <main className="flex flex-1 flex-col bg-bg-surface pt-2">
        <div className="bg-bg-surface">
          <div className="relative h-84 overflow-hidden">
            {race.images[0] ? (
              <Image
                src={race.images[0]}
                alt=""
                fill
                priority
                sizes="600px"
                className="object-cover"
              />
            ) : (
              <RacePoster seed={race.id} title="" label="" className="absolute inset-0" />
            )}
            {race.images.length > 0 && (
              <span className="absolute right-4 bottom-4 rounded-full bg-bg-overlay/40 px-3 py-1 text-caption-medium text-text-on-hero">
                1 / {race.images.length}
              </span>
            )}
          </div>

          <div className="flex flex-col gap-3 px-4 py-4">
            <h2 className="text-title2">{race.title}</h2>
            <p className="flex items-center gap-2 text-caption-regular text-text-secondary">
              <Icon name="Calendar" className="size-4" />
              {race.startsAt}
            </p>
            <p className="flex items-center gap-2 text-caption-regular text-text-secondary">
              <Icon name="MapPin" className="size-4" />
              {race.place}
            </p>
            <p className="flex items-center gap-2 text-caption-regular text-text-secondary">
              <Icon name="Users" className="size-4" />
              {capacityLine(race.dDay, race.capacity)}
            </p>
            <p className="flex items-baseline gap-2">
              <span className="text-title2">{formatPrice(minPrice)}</span>
              <span className="text-caption-regular text-text-secondary">부터</span>
            </p>
          </div>
        </div>

        {inParty && !joinedAsMember && !alreadyApplied && (
          <p className="bg-bg-surface px-4 pb-4">
            <Badge>
              내 파티 · {race.options.find((o) => o.id === String(party.raceCourseId))?.name} ·{' '}
              {party.memberCount}명
            </Badge>
          </p>
        )}

        {race.crew && (
          <CrewSummary crew={race.crew} saleOpen={race.schedule[0]?.value.split(' ~ ')[0]} />
        )}

        <div className="sticky top-(--layout-header-height) z-[5] bg-bg-surface px-4 py-2">
          <nav aria-label="대회 상세 바로 가기" className="flex border-b border-border-default">
            {anchors.map((item) => (
              <a
                key={item.value}
                href={`#race-${item.value}`}
                aria-current={item.value === anchor ? 'location' : undefined}
                onClick={(event) => {
                  event.preventDefault()
                  jumpTo(item.value)
                }}
                className={cn(
                  'relative flex min-w-0 flex-1 items-center justify-center py-3 text-body1-medium text-text-tertiary',
                  'aria-[current=location]:font-bold aria-[current=location]:text-text-primary',
                  'after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:rounded-[1px] aria-[current=location]:after:bg-text-primary',
                  FOCUS_RING,
                )}
              >
                <span className="truncate whitespace-nowrap">{item.label}</span>
              </a>
            ))}
          </nav>
        </div>

        <Section title="종목 선택" className="scroll-mt-32 pt-6 pb-4">
          <div id="race-options" className="flex flex-col gap-3">
            {race.options.map((option) => (
              <OptionCard
                key={option.id}
                option={option}
                selected={option.id === optionId}
                onSelect={() => setOptionId(option.id)}
              />
            ))}
          </div>
        </Section>

        <div className="flex flex-col px-4 pt-4 pb-2">
          <InfoRow id="race-schedule" label="접수">
            <p className="text-body1-medium text-text-primary">{race.schedule[0]?.value ?? '-'}</p>
            <p className="text-body1-regular text-text-secondary">
              정보 수정은 {race.infoEditDeadline}까지
            </p>
          </InfoRow>
          <InfoRow id="race-refund" label="취소·환불" divided>
            <div className="flex flex-col gap-2">
              <p className="text-body1-medium text-text-primary">
                환불은 <b className="font-bold">예치금</b>으로 돌려드려요
              </p>
              {refundDeadline !== '-' && (
                <>
                  <RefundBox description="개인 사정으로 취소해도 전액 돌려드려요">
                    <span className="text-text-brand">{refundDeadline}</span>
                    <span>까지 취소하면 전액 환불</span>
                  </RefundBox>
                  <RefundBox description="개인 사정으로 취소하면 돌려드리지 않아요">
                    <span>그 뒤에 취소하면 </span>
                    <span className="text-text-danger">환불 불가</span>
                  </RefundBox>
                </>
              )}
            </div>
          </InfoRow>
          <InfoRow label="키트 배송" divided>
            <p className="text-body1-regular text-text-primary">
              <b className="font-bold">환불 기한이 지난 뒤</b> 배송을 시작해요
            </p>
          </InfoRow>
          <InfoRow label="양도" divided>
            <p className="text-body1-regular text-text-primary">
              참가권은 다른 사람에게 <b className="font-bold text-text-danger">양도할 수 없어요</b>
            </p>
          </InfoRow>
        </div>

        {hasInfo && (
          <Section title="상세 정보" className="scroll-mt-32 pt-6 pb-4">
            <div id="race-info" className="flex flex-col gap-3">
              {race.description && (
                <p className="text-body2-regular whitespace-pre-line">{race.description}</p>
              )}
              {race.images.map((src, i) => (
                <div key={`${src}-${i}`} className="relative h-112 overflow-hidden rounded-md">
                  <Image
                    src={src}
                    alt={`상세 정보 이미지 ${i + 1}`}
                    fill
                    sizes="568px"
                    className="object-cover"
                  />
                </div>
              ))}
            </div>
          </Section>
        )}

        {race.faq.length > 0 && (
          <Section title="자주 묻는 질문" className="pt-6 pb-4">
            <FaqList items={race.faq} />
          </Section>
        )}

        <SimilarRaces raceId={race.id} />

        <Section title="문의">
          <Link
            href={`/me/inquiries/new?raceId=${race.id}` as Route}
            className={buttonClassName({ variant: 'ghost' })}
          >
            이 대회에 문의하기
          </Link>
        </Section>

        <section className="flex flex-col gap-2 bg-bg-surface px-4 pt-6 pb-4">
          <p className="text-body1-medium">{race.seller.name}</p>
          <p className="text-caption-regular text-text-tertiary">{race.seller.businessNo}</p>
          <p className="text-caption-regular text-text-tertiary">{race.seller.mailOrderNo}</p>
        </section>
      </main>
      <ApplyCta
        race={race}
        optionId={optionId}
        alreadyApplied={alreadyApplied}
        reapplyBlocked={reapplyBlocked}
        party={party}
      />
      <RaceShareSheet
        open={shareOpen}
        onOpenChange={setShareOpen}
        title={race.title}
        url={`${typeof window === 'undefined' ? '' : window.location.origin}/races/${race.id}`}
      />
    </>
  )
}

// 피그마 대회 상세(크루 전용 자리): 요약 아래 테두리 카드. 크루 선예매 · 일반 판매 오픈 · 파티 인원
function CrewSummary({
  crew,
  saleOpen,
}: {
  crew: NonNullable<RaceDetail['crew']>
  saleOpen?: string
}) {
  const now = useNow()
  const active =
    !!now &&
    (!crew.startAt || now >= parseKstDateTime(crew.startAt)) &&
    (!crew.endAt || now < parseKstDateTime(crew.endAt))
  return (
    <div className="px-4 pb-4">
      <dl className="flex flex-col gap-3 rounded-lg border border-border-default bg-bg-surface p-4 shadow-sm">
        {crew.endAt && (
          <CrewRow label="크루 선예매">
            <span className={active ? 'text-text-brand' : undefined}>
              {formatRaceDateTime(crew.endAt)} 마감{active && ' · 진행 중'}
            </span>
          </CrewRow>
        )}
        {saleOpen && <CrewRow label="일반 판매 오픈">{saleOpen}</CrewRow>}
        {crew.minCount !== undefined && (
          <CrewRow label="파티 인원">{`${crew.minCount}명 ~ ${crew.maxCount ?? crew.minCount}명`}</CrewRow>
        )}
      </dl>
    </div>
  )
}

function CrewRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex gap-4 text-body1-regular">
      <dt className="w-24 shrink-0 text-text-secondary">{label}</dt>
      <dd className="min-w-0 flex-1 text-text-primary">{children}</dd>
    </div>
  )
}

function OptionCard({
  option,
  selected,
  onSelect,
}: {
  option: RaceOption
  selected: boolean
  onSelect: () => void
}) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      disabled={option.soldOut}
      onClick={onSelect}
      className={cn(
        'flex h-27 items-center gap-4 rounded-md bg-bg-surface p-4 text-left',
        selected
          ? 'inset-ring-2 inset-ring-text-primary'
          : 'shadow-sm inset-ring inset-ring-border-default',
        FOCUS_RING,
      )}
    >
      <span className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="text-body1-bold">
          {option.name}
          {option.soldOut && <span className="text-text-tertiary"> · 정원 마감</span>}
        </span>
        <span className="text-body1-regular text-text-secondary">{option.includes}</span>
      </span>
      <span className="text-body1-bold text-text-primary">{formatPrice(option.price)}</span>
    </button>
  )
}

// 접수 · 취소 안내의 한 줄: 왼쪽 이름 칸 84, 오른쪽 내용
function InfoRow({
  id,
  label,
  divided,
  children,
}: {
  id?: string
  label: string
  divided?: boolean
  children: ReactNode
}) {
  return (
    <div
      id={id}
      className={cn('flex scroll-mt-32 gap-4 py-4', divided && 'border-t border-border-default')}
    >
      <p className="w-21 shrink-0 text-body1-regular text-text-secondary">{label}</p>
      <div className="flex min-w-0 flex-1 flex-col gap-1">{children}</div>
    </div>
  )
}

function RefundBox({ description, children }: { description: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5 rounded-sm border border-border-default px-4 py-3">
      <p className="text-body1-bold text-text-primary">{children}</p>
      <p className="text-body1-regular text-text-primary">{description}</p>
    </div>
  )
}

function FaqList({ items }: { items: RaceDetail['faq'] }) {
  return (
    <ul className="flex flex-col">
      {items.map((item) => (
        <li key={item.question}>
          <details className="group">
            <summary
              className={cn(
                'flex h-14 cursor-pointer list-none items-center gap-3 rounded-md [&::-webkit-details-marker]:hidden',
                FOCUS_RING,
              )}
            >
              <span className="flex-1 truncate text-body1-regular">{item.question}</span>
              <Icon
                name="ChevronRight"
                className="size-5 text-text-tertiary transition-transform group-open:rotate-90"
              />
            </summary>
            <p className="pb-4 text-body2-regular text-text-secondary">{item.answer}</p>
          </details>
        </li>
      ))}
    </ul>
  )
}

// dDay는 D-12 · D-DAY · 마감, 마감일이 없으면 -
function capacityLine(dDay: string, capacity: number) {
  const close = dDay === '-' ? null : dDay === '마감' ? '접수 마감' : `접수 마감 ${dDay}`
  return [close, `정원 ${formatNumber(capacity)}명`].filter(Boolean).join(' · ')
}
