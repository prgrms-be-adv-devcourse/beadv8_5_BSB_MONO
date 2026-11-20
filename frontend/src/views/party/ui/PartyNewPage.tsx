'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery } from '@tanstack/react-query'
import type { Route } from 'next'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useState } from 'react'
import { useForm } from 'react-hook-form'

import { AppHeader } from '@/widgets/app-header'
import { formatKstDateTime, formatKstLastDay, parseKstDateTime } from '@/entities/crew'
import {
  crewPartyRestrictedEndAt,
  individualParticipationId,
  partyFailureOf,
  useCreateParty,
  type PartyFailure,
} from '@/entities/party'
import { fetchRacePage, raceQueryOptions, RaceSort } from '@/entities/race'
import { ApiError, memberApi, productApi, productModel, unwrapData } from '@/shared/api'
import { cn, formatNumber, useNow } from '@/shared/lib'
import {
  Alert,
  Badge,
  BottomCta,
  Button,
  buttonClassName,
  Dialog,
  EmptyState,
  Radio,
  RadioGroup,
  Skeleton,
} from '@/shared/ui'

import { CREATE_JOINED_LATE, JOIN_FAILURE } from '../lib/party-copy'
import {
  EMPTY_PARTICIPANT,
  participantSchemaFor,
  toParticipantRequest,
  type ParticipantValues,
} from '../model/participant-form'
import { IndividualExistsDialog } from './IndividualExistsDialog'
import { ParticipantFields } from './ParticipantFields'
import { PartyFailureScreen, PartyLoadError } from './PartyFailureScreen'

const FORM_ID = 'party-new-form'

const CREW_RACES_PARAMS = { sort: RaceSort.DEADLINE, size: 20 }

type RacePhase = 'before' | 'open' | 'ended'

function crewPhase(race: productModel.RaceSummaryDto, now: number): RacePhase {
  if (race.crewSaleStartAt && parseKstDateTime(race.crewSaleStartAt) > now) return 'before'
  if (race.crewSaleEndAt && parseKstDateTime(race.crewSaleEndAt) <= now) return 'ended'
  return 'open'
}

// 피그마 파티 02 대회·종목 고르기 → 내 참가 정보 → 만들기. 03 · 03-1 · 27은 만들 때 막힌 경우.
// 파티 만들기 API가 파티장의 참가 정보와 동의를 함께 받아서, 시안의 「파티원 고르기」 자리에 이 입력을 둔다.
export function PartyNewPage({ crewId }: { crewId?: number }) {
  const router = useRouter()
  const nowMs = useNow()?.getTime()
  const races = useQuery({
    // 생성 훅은 응답 전체를, fetchRacePage는 data만 담으므로 같은 키를 쓰지 않게 꼬리를 붙인다
    queryKey: [...productApi.getSearchQueryKey(CREW_RACES_PARAMS), 'page'],
    queryFn: () => fetchRacePage(CREW_RACES_PARAMS),
    enabled: crewId !== undefined,
  })
  const [raceId, setRaceId] = useState<number>()
  const [courseId, setCourseId] = useState<number>()
  const [step, setStep] = useState<1 | 2>(1)
  const [blocked, setBlocked] = useState<PartyFailure>()
  const [individualId, setIndividualId] = useState<number>()
  const [serverMessage, setServerMessage] = useState<string>()
  // 결제율 미달로 크루의 파티 만들기가 제한된 경우, 제한이 끝나는 시각
  const [restrictedEnd, setRestrictedEnd] = useState<string>()
  const detail = useQuery({
    ...raceQueryOptions(raceId ?? 0),
    enabled: raceId !== undefined,
  })
  const crew = memberApi.useFindCrew(crewId ?? 0, {
    query: { enabled: crewId !== undefined, select: unwrapData },
  })
  const create = useCreateParty()
  // 고른 종목에 키트가 들어 있으면 키트 배송지도 받는다
  const pickedCourse = detail.data?.courses?.find((c) => c.raceCourseId === courseId)
  const kit = pickedCourse?.kitIncluded === true
  const sizes = pickedCourse?.tshirtSizeOption ?? []
  const schema = participantSchemaFor(kit, sizes)
  const form = useForm<ParticipantValues>({
    resolver: zodResolver(schema),
    defaultValues: EMPTY_PARTICIPANT,
  })

  const submit = form.handleSubmit((values) => {
    if (crewId === undefined || courseId === undefined) return
    setServerMessage(undefined)
    create.mutate(
      {
        crewId,
        raceCourseId: courseId,
        participant: toParticipantRequest(schema.parse(values), kit),
        thirdPartyConsent: true,
      },
      {
        onSuccess: ({ party }) => router.replace(`/parties/${party.id}` as Route),
        onError: (error) => {
          const failure = partyFailureOf(error)
          const restricted = crewPartyRestrictedEndAt(error)
          if (restricted) setRestrictedEnd(restricted)
          else if (failure === 'INDIVIDUAL_EXISTS')
            setIndividualId(individualParticipationId(error))
          else if (failure) setBlocked(failure)
          else
            setServerMessage(
              (error instanceof ApiError && error.serverMessage) ||
                '파티를 만들지 못했어요. 잠시 후 다시 시도해 주세요.',
            )
        },
      },
    )
  })

  // 시안 2074:95341: 고를 대회가 하나도 없으면 제목과 안내, 대회 찾기 버튼만 보여 준다
  const noRaces =
    crewId !== undefined &&
    !blocked &&
    step === 1 &&
    races.isSuccess &&
    !(races.data.races ?? []).some((r) => r.crewSaleStartAt)

  let body
  if (crewId === undefined) {
    body = (
      <EmptyState
        icon={null}
        className="pt-26"
        title="크루 홈에서 파티를 만들 수 있어요"
        action={
          <Link href="/crews" className={buttonClassName({ variant: 'secondary', size: 'm' })}>
            내 크루로 가기
          </Link>
        }
      />
    )
  } else if (blocked) {
    // 파티를 만들 때의 「크루 가입이 늦어요」는 만들기 문구, 나머지는 합류와 같은 이유 문구
    body = (
      <PartyFailureScreen
        copy={
          blocked === 'JOINED_LATE'
            ? CREATE_JOINED_LATE
            : JOIN_FAILURE[blocked as keyof typeof JOIN_FAILURE]
        }
        crewId={blocked === 'JOINED_LATE' ? undefined : crewId}
      />
    )
  } else if (step === 2) {
    body = (
      <form
        id={FORM_ID}
        onSubmit={submit}
        noValidate
        className="flex flex-col gap-4 bg-bg-surface px-4 py-6"
      >
        <div className="flex flex-col gap-1">
          <h2 className="text-title1">내 참가 정보를 적어 주세요</h2>
          <p className="text-body2-regular text-text-secondary">
            {detail.data?.title} · 2/2 참가 정보
          </p>
        </div>
        {serverMessage && <Alert tone="danger" title={serverMessage} />}
        <ParticipantFields form={form} kit={kit} sizes={sizes} />
      </form>
    )
  } else if (noRaces) {
    body = (
      <div className="flex flex-col gap-6 bg-bg-surface px-4 py-6">
        <h2 className="text-title1">어느 대회에 나갈까요?</h2>
        <EmptyState
          icon="Message"
          className="py-0"
          title="지금 고를 수 있는 대회가 없어요"
          action={
            <Link href="/races" className={buttonClassName({ variant: 'primary', size: 'm' })}>
              대회 찾기
            </Link>
          }
        />
      </div>
    )
  } else if (races.isPending) {
    body = (
      <div className="flex flex-col gap-3 p-4" aria-busy>
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-24 w-full" />
      </div>
    )
  } else if (races.isError) {
    body = <PartyLoadError onRetry={() => void races.refetch()} />
  } else {
    const list = (races.data.races ?? []).filter((r) => r.crewSaleStartAt)
    body = (
      <div className="flex flex-col gap-6 bg-bg-surface px-4 py-6">
        <div className="flex flex-col gap-1">
          <h2 className="text-title1">어느 대회, 어느 종목으로 나갈까요?</h2>
          <p className="text-body2-regular text-text-secondary">
            {crew.data ? `${crew.data.name} 크루로 파티를 만들어요 · ` : ''}1/2 대회·종목 고르기
          </p>
        </div>
        <Alert
          title="파티원은 모두 이 종목으로 신청해요"
          description="만든 뒤에는 종목을 바꿀 수 없어요. 바꾸려면 파티를 새로 만들어야 해요."
        />
        {list.length === 0 ? (
          <EmptyState icon={null} title="크루 예매 중인 대회가 없어요" />
        ) : (
          <ul className="flex flex-col gap-3">
            {list.map((race) => {
              const phase = nowMs === undefined ? 'before' : crewPhase(race, nowMs)
              const selected = race.raceId === raceId
              const disabled = phase !== 'open'
              return (
                <li key={race.raceId}>
                  <button
                    type="button"
                    disabled={disabled}
                    aria-pressed={selected}
                    onClick={() => {
                      setRaceId(race.raceId)
                      setCourseId(undefined)
                    }}
                    className={cn(
                      'flex w-full flex-col items-start gap-2 rounded-lg border p-4 text-left',
                      selected ? 'border-2 border-border-brand' : 'border-border-default',
                      disabled && 'opacity-40',
                    )}
                  >
                    <span className="flex w-full items-center justify-between">
                      <Badge>
                        {phase === 'open'
                          ? '크루 예매 중'
                          : phase === 'before'
                            ? '크루 예매 전'
                            : '크루 예매 끝'}
                      </Badge>
                      {selected && (
                        <span className="text-body2-medium text-text-brand">선택됨</span>
                      )}
                    </span>
                    <span className="text-body1-medium">{race.title}</span>
                    <span className="text-caption-regular text-text-secondary">
                      {phase === 'ended' && race.crewSaleEndAt
                        ? `크루 예매가 ${formatKstDateTime(race.crewSaleEndAt)}에 끝나 고를 수 없어요`
                        : race.crewSaleEndAt
                          ? `크루 예매 ${formatKstDateTime(race.crewSaleEndAt)}까지`
                          : ''}
                    </span>
                  </button>
                </li>
              )
            })}
          </ul>
        )}
        {raceId !== undefined && (
          <section className="flex flex-col gap-3">
            <h3 className="text-body1-medium">종목</h3>
            {detail.isPending ? (
              <Skeleton className="h-12 w-full" />
            ) : (
              <RadioGroup
                value={courseId === undefined ? '' : String(courseId)}
                onValueChange={(v) => setCourseId(Number(v))}
              >
                {(detail.data?.courses ?? []).map((c) => (
                  <Radio
                    key={c.raceCourseId}
                    value={String(c.raceCourseId)}
                    disabled={(c.crewRemaining ?? 0) <= 0}
                    label={`${c.name} · ${formatNumber(c.price ?? 0)}원 · 남은 크루 전용 자리 ${c.crewRemaining ?? 0}자리`}
                  />
                ))}
              </RadioGroup>
            )}
          </section>
        )}
      </div>
    )
  }

  const picked = detail.data?.courses?.find((c) => c.raceCourseId === courseId)
  return (
    <>
      <AppHeader
        type="sub"
        title={blocked === 'JOINED_LATE' || noRaces ? '대회 정하기' : '파티 만들기'}
      />
      <main className="flex flex-1 flex-col">{body}</main>
      {crewId !== undefined && !blocked && !noRaces && (
        <BottomCta
          className="sticky bottom-0 z-10"
          primary={
            step === 1 ? (
              <Button size="l" disabled={courseId === undefined} onClick={() => setStep(2)}>
                {picked ? `${picked.name}로 참가 정보 적기` : '종목을 골라 주세요'}
              </Button>
            ) : (
              <Button size="l" type="submit" form={FORM_ID} loading={create.isPending}>
                파티 만들기
              </Button>
            )
          }
        />
      )}
      <Dialog
        open={restrictedEnd !== undefined}
        onOpenChange={(open) => !open && setRestrictedEnd(undefined)}
        type="alert"
        title="파티를 만들 수 없어요"
        body={restrictedEnd ? `${formatKstLastDay(restrictedEnd)}까지 파티 만들기가 제한돼요.` : ''}
        confirmLabel="크루 홈으로"
        onConfirm={() => router.push(`/crews/${crewId}` as Route)}
      />
      {individualId !== undefined && (
        <IndividualExistsDialog
          participationId={individualId}
          onClose={() => {
            setIndividualId(undefined)
            setStep(1)
          }}
          onCanceled={() => {
            setIndividualId(undefined)
            void submit()
          }}
        />
      )}
    </>
  )
}
