'use client'

import type { Route } from 'next'
import Image from 'next/image'
import Link from 'next/link'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { RaceShareSheet } from '@/widgets/race-share-sheet'
import { useAuthStatus } from '@/features/auth'
import { useMyRaceStatus } from '@/entities/party'
import { formatRaceDateTime, RacePoster, type RaceDetail, type RaceOption } from '@/entities/race'
import { cn, formatNumber, formatWon, useTrackOnce } from '@/shared/lib'
import {
  Alert,
  Badge,
  FOCUS_RING,
  Icon,
  IconButton,
  KeyValue,
  Section,
  Segmented,
  StatTile,
} from '@/shared/ui'

import { ApplyCta } from './ApplyCta'
import { SimilarRaces } from './SimilarRaces'

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
  const posterLabel = race.options.map((o) => o.name).join(' · ')
  const [anchor, setAnchor] = useState<Anchor>('options')
  const [infoTab, setInfoTab] = useState<'detail' | 'faq'>('detail')
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
      <main className="flex flex-1 flex-col gap-2 bg-bg-surface">
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
            <div className="absolute inset-x-0 bottom-0 h-35 bg-linear-to-b from-bg-overlay/0 to-bg-overlay/55" />
            <div className="absolute bottom-5.5 left-5.5 flex flex-col gap-1.5 text-text-on-hero">
              <p className="text-title3">{race.heroTitle}</p>
              <p className="text-body2-medium whitespace-pre opacity-85">{race.heroTags}</p>
            </div>
            {race.images.length > 0 && (
              <span className="absolute right-4 bottom-4.5 rounded-full bg-bg-overlay/40 px-3 py-1 text-caption-medium text-text-on-hero">
                1 / {race.images.length}
              </span>
            )}
          </div>

          <div className="flex flex-col gap-3 px-4 py-5">
            <div className="flex gap-1">
              <Badge tone={race.status.tone}>{race.status.label}</Badge>
            </div>
            <h2 className="text-title1">{race.title}</h2>
            <p className="flex items-center gap-2 text-body2-regular text-text-secondary">
              <Icon name="Calendar" className="size-4" />
              {race.startsAt}
            </p>
            <p className="flex items-center gap-2 text-body2-regular text-text-secondary">
              <Icon name="MapPin" className="size-4" />
              {race.place}
            </p>
            <p className="flex items-baseline gap-2">
              <span className="text-title2">{formatWon(minPrice)}</span>
              <span className="text-body2-regular text-text-secondary">부터</span>
            </p>
          </div>

          <div className="flex gap-2 px-4 pb-5">
            <StatTile size="s" label="접수 마감" value={race.dDay} />
            <StatTile size="s" label="정원" value={formatNumber(race.capacity)} unit="명" />
          </div>
        </div>

        {(alreadyApplied || joinedAsMember) && (
          <div className="bg-bg-surface px-4 py-4">
            <Alert
              title={alreadyApplied ? '이 대회에 이미 신청했어요' : '파티로 신청 중인 대회예요'}
              description={
                <>
                  한 사람은 한 대회에 1건만 가질 수 있어요.
                  <br />
                  {alreadyApplied
                    ? '내 참가 대회에서 확인해 주세요.'
                    : '개인으로 나가려면 파티에서 먼저 나가 주세요.'}
                </>
              }
            />
          </div>
        )}
        {inParty && !joinedAsMember && !alreadyApplied && (
          <p className="bg-bg-surface px-4 pb-4">
            <Badge>
              내 파티 · {race.options.find((o) => o.id === String(party.raceCourseId))?.name} ·{' '}
              {party.memberCount}명
            </Badge>
          </p>
        )}

        <div className="sticky top-(--layout-header-height) z-[5] bg-bg-surface">
          <Segmented label="바로 가기" items={[...ANCHORS]} value={anchor} onChange={jumpTo} />
        </div>

        <Section title="종목 선택" className="scroll-mt-32">
          <div id="race-options" className="flex flex-col gap-4">
            {race.options.map((option) => (
              <OptionCard
                key={option.id}
                option={option}
                image={option.image ?? race.images[0]}
                posterSeed={race.id}
                posterLabel={posterLabel}
                selected={option.id === optionId}
                onSelect={() => setOptionId(option.id)}
              />
            ))}
          </div>
        </Section>

        {race.crew && (
          <Section title="크루원과 함께 신청하려면">
            <div className="flex flex-col gap-4">
              {race.crew.endAt && (
                <KeyValue label="크루 전용 자리 마감" value={formatRaceDateTime(race.crew.endAt)} />
              )}
              {race.crew.minCount !== undefined && (
                <KeyValue
                  label="파티 인원"
                  value={`${race.crew.minCount}명 ~ ${race.crew.maxCount ?? race.crew.minCount}명`}
                />
              )}
            </div>
          </Section>
        )}

        <Section title="접수 일정">
          <div id="race-schedule" className="flex scroll-mt-32 flex-col gap-4">
            {race.schedule.slice(0, 1).map((row) => (
              <KeyValue key={row.label} label={row.label} value={row.value} />
            ))}
            <KeyValue label="정보 수정 기한" value={race.infoEditDeadline} />
          </div>
        </Section>

        <Section title="취소 · 환불">
          <div id="race-refund" className="flex scroll-mt-32 flex-col gap-4">
            {race.refund.map((row) => (
              <KeyValue key={row.label} label={row.label} value={row.value} />
            ))}
            <p className="text-caption-regular text-text-tertiary">
              환불은 예치금으로 돌려드려요. 키트는 환불 기한이 지난 뒤 배송해요.
            </p>
            <p className="text-caption-regular text-text-tertiary">
              참가권은 다른 사람에게 양도할 수 없어요.
            </p>
          </div>
        </Section>

        <Section className="pt-0">
          <div id="race-info" className="flex scroll-mt-32 flex-col gap-4">
            <Segmented
              label="상세 정보"
              items={[
                { value: 'detail', label: '상세 정보' },
                { value: 'faq', label: '자주 묻는 질문' },
              ]}
              value={infoTab}
              onChange={setInfoTab}
            />
            {infoTab === 'detail' ? (
              <>
                {race.description && (
                  <p className="text-body2-regular whitespace-pre-line">{race.description}</p>
                )}
                {race.images.map((src, i) => (
                  <div key={src} className="relative h-112 overflow-hidden rounded-md">
                    <Image
                      src={src}
                      alt={`상세 정보 이미지 ${i + 1}`}
                      fill
                      sizes="448px"
                      className="object-cover"
                    />
                  </div>
                ))}
                {!race.description && race.images.length === 0 && (
                  <p className="py-6 text-center text-body2-regular text-text-tertiary">
                    등록된 상세 정보가 없어요
                  </p>
                )}
              </>
            ) : (
              <FaqList items={race.faq} />
            )}
          </div>
        </Section>

        <SimilarRaces raceId={race.id} />

        <section className="flex flex-col gap-2 bg-bg-surface px-4 py-6">
          <p className="text-body2-medium">{race.seller.name}</p>
          <p className="text-caption-regular text-text-tertiary">{race.seller.businessNo}</p>
          <p className="text-caption-regular text-text-tertiary">{race.seller.mailOrderNo}</p>
        </section>
        <Link
          href={`/me/inquiries/new?raceId=${race.id}` as Route}
          className="self-start px-4 pt-2 pb-6 text-caption-regular text-text-tertiary underline"
        >
          이 대회에 문의하기
        </Link>
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

function OptionCard({
  option,
  image,
  posterSeed,
  posterLabel,
  selected,
  onSelect,
}: {
  option: RaceOption
  image?: string
  posterSeed: string
  posterLabel: string
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
        'flex h-27 items-center gap-4 rounded-lg p-4 text-left',
        selected
          ? 'bg-bg-brand-subtle inset-ring-2 inset-ring-border-brand'
          : 'bg-bg-surface inset-ring inset-ring-border-default',
        FOCUS_RING,
      )}
    >
      {image ? (
        <Image
          src={image}
          alt=""
          width={72}
          height={72}
          className="size-18 rounded-md object-cover"
        />
      ) : (
        <RacePoster
          seed={posterSeed}
          title=""
          label={posterLabel}
          className="size-18 shrink-0 rounded-md"
        />
      )}
      <span className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="text-body2-bold">
          {option.name}
          {option.soldOut && <span className="text-text-tertiary"> · 정원 마감</span>}
        </span>
        <span className="text-caption-regular text-text-secondary">{option.includes}</span>
      </span>
      <span className="text-body2-bold text-text-primary">{formatWon(option.price)}</span>
    </button>
  )
}

function FaqList({ items }: { items: RaceDetail['faq'] }) {
  if (items.length === 0) {
    return (
      <p className="py-6 text-center text-body2-regular text-text-tertiary">등록된 질문이 없어요</p>
    )
  }

  return (
    <ul className="flex flex-col divide-y divide-border-default">
      {items.map((item) => (
        <li key={item.question}>
          <details className="group">
            <summary
              className={cn(
                'flex cursor-pointer list-none items-center gap-3 rounded-md py-4 [&::-webkit-details-marker]:hidden',
                FOCUS_RING,
              )}
            >
              <span className="text-body2-bold text-text-brand">Q</span>
              <span className="flex-1 text-body2-medium">{item.question}</span>
              <Icon
                name="ChevronDown"
                className="size-5 text-text-tertiary transition-transform group-open:rotate-180"
              />
            </summary>
            <p className="pb-4 pl-6 text-body2-regular text-text-secondary">{item.answer}</p>
          </details>
        </li>
      ))}
    </ul>
  )
}
