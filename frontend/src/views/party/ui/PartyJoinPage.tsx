'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import type { Route } from 'next'
import { useRouter } from 'next/navigation'
import { useState } from 'react'
import { useForm } from 'react-hook-form'

import { AppHeader } from '@/widgets/app-header'
import { formatKstDateTime } from '@/entities/crew'
import {
  individualParticipationId,
  partyFailureOf,
  useJoinParty,
  usePartyPreview,
  type PartyFailure,
} from '@/entities/party'
import { ApiError, memberApi, productApi, unwrapData } from '@/shared/api'
import { formatNumber } from '@/shared/lib'
import { Alert, BottomCta, Button, Skeleton } from '@/shared/ui'

import { JOIN_FAILURE } from '../lib/party-copy'
import {
  EMPTY_PARTICIPANT,
  participantSchema,
  toParticipantRequest,
  type ParticipantValues,
} from '../model/participant-form'
import { IndividualExistsDialog } from './IndividualExistsDialog'
import { ParticipantFields } from './ParticipantFields'
import { PartyFailureScreen, PartyLoadError } from './PartyFailureScreen'

const FORM_ID = 'party-join-form'

// 피그마 파티 08 합류 확인 · 09~15 · 25 · 26. 링크를 열면 파티 미리 보기로 종목을 읽기 전용으로 보여 주고,
// 합류 요청이 막히면 이유별 화면으로 바꾼다. 입력값 오류(400)는 서버 문구를 폼 위에 보여 준다.
export function PartyJoinPage({ token }: { token: string }) {
  const router = useRouter()
  const preview = usePartyPreview(token)
  const join = useJoinParty()
  const data = preview.data
  const race = productApi.useFindRace(data?.raceId ?? 0, {
    query: { enabled: data !== undefined, select: unwrapData },
  })
  const crew = memberApi.useFindCrew(data?.crewId ?? 0, {
    query: { enabled: data !== undefined, select: unwrapData, retry: false },
  })
  const [blocked, setBlocked] = useState<PartyFailure>()
  const [individualId, setIndividualId] = useState<number>()
  const [serverMessage, setServerMessage] = useState<string>()
  const form = useForm<ParticipantValues>({
    resolver: zodResolver(participantSchema),
    defaultValues: EMPTY_PARTICIPANT,
  })

  const submit = form.handleSubmit((values) => {
    setServerMessage(undefined)
    join.mutate(
      {
        joinToken: token,
        participant: toParticipantRequest(participantSchema.parse(values)),
        thirdPartyConsent: true,
      },
      {
        onSuccess: (participation) => router.replace(`/parties/${participation.partyId}` as Route),
        onError: (error) => {
          const failure = partyFailureOf(error)
          if (failure === 'INDIVIDUAL_EXISTS') setIndividualId(individualParticipationId(error))
          else if (failure) setBlocked(failure)
          else
            setServerMessage(
              (error instanceof ApiError && error.serverMessage) ||
                '지금은 합류할 수 없어요. 잠시 후 다시 시도해 주세요.',
            )
        },
      },
    )
  })

  const failure: PartyFailure | undefined =
    blocked ??
    (preview.error ? partyFailureOf(preview.error) : undefined) ??
    (data && !data.joinLinkAlive ? 'LINK_EXPIRED' : undefined) ??
    (data && data.status !== 'RECRUITING' ? 'NOT_RECRUITING' : undefined)
  const course = race.data?.courses?.find((c) => c.raceCourseId === data?.raceCourseId)

  let body
  if (failure && failure !== 'INDIVIDUAL_EXISTS') {
    body = <PartyFailureScreen copy={JOIN_FAILURE[failure]} crewId={data?.crewId} />
  } else if (preview.isPending) {
    body = (
      <div className="flex flex-col gap-4 p-4" aria-busy>
        <Skeleton className="h-8 w-2/3" />
        <Skeleton className="h-40 w-full" />
      </div>
    )
  } else if (!data) {
    body = <PartyLoadError onRetry={() => void preview.refetch()} />
  } else {
    body = (
      <>
        <section className="flex flex-col gap-1 bg-bg-surface px-4 py-6">
          <h2 className="text-title1">{race.data?.title ?? <Skeleton className="h-8 w-2/3" />}</h2>
          <p className="text-body2-regular text-text-secondary">
            {[
              crew.data && `${crew.data.name} 크루`,
              course?.name,
              `파티장 ${data.leaderNickname}`,
              `지금 ${data.memberCount}명`,
            ]
              .filter(Boolean)
              .join(' · ')}
          </p>
        </section>
        <form
          id={FORM_ID}
          onSubmit={submit}
          noValidate
          className="flex flex-col gap-4 bg-bg-surface px-4 pb-6"
        >
          <Alert
            title={`이 링크로 ${formatKstDateTime(data.joinTokenExpiresAt)}까지 합류할 수 있어요`}
            description="파티장이 링크를 새로 만들면 이 링크는 더 쓸 수 없어요."
          />
          {serverMessage && <Alert tone="danger" title={serverMessage} />}
          <ParticipantFields
            form={form}
            course={course ? `${course.name} · ${formatNumber(course.price ?? 0)}원` : undefined}
          />
        </form>
      </>
    )
  }

  return (
    <>
      <AppHeader type="sub" title="파티 합류" />
      <main className="flex flex-1 flex-col">{body}</main>
      {data && !failure && (
        <BottomCta
          className="sticky bottom-0 z-10"
          primary={
            <Button size="l" type="submit" form={FORM_ID} loading={join.isPending}>
              파티에 합류하기
            </Button>
          }
        />
      )}
      {individualId !== undefined && (
        <IndividualExistsDialog
          participationId={individualId}
          onClose={() => setIndividualId(undefined)}
          onCanceled={() => {
            setIndividualId(undefined)
            void submit()
          }}
        />
      )}
    </>
  )
}
