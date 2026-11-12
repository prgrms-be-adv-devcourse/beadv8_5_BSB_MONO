'use client'

import type { Route } from 'next'
import { useRouter } from 'next/navigation'
import { useEffect, useRef, useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import {
  isCrewSeatShortage,
  useApplyParty,
  usePartyDetail,
  usePartyQueueStatus,
  useRegisterPartyQueue,
} from '@/entities/party'
import { ApiError, memberApi, productApi, unwrapData } from '@/shared/api'
import {
  Alert,
  BottomCta,
  Button,
  Divider,
  EmptyState,
  KeyValue,
  ProgressBar,
  Skeleton,
} from '@/shared/ui'

import { PartyLoadError } from './PartyFailureScreen'

type Shortage = { needed: number; remaining: number }

// 피그마 M33-P 예매창 · 파티 신청 → 대기열 → 신청. 크루 전용 자리가 모자라면 V3 · M14 결과를 보여 준다.
// 파티를 신청하려면 파티 대기열에 먼저 들어가야 하고(서버가 입장 중인지 확인한다), 차례가 오면 신청 API를 부른다.
export function PartyApplyPage({ partyId }: { partyId: number }) {
  const router = useRouter()
  const party = usePartyDetail(partyId)
  const data = party.data
  const race = productApi.useFindRace(data?.raceId ?? 0, {
    query: { enabled: data !== undefined, select: unwrapData },
  })
  const crew = memberApi.useFindCrew(data?.crewId ?? 0, {
    query: { enabled: data !== undefined, select: unwrapData },
  })
  const register = useRegisterPartyQueue()
  const apply = useApplyParty()
  const [queued, setQueued] = useState(false)
  const [message, setMessage] = useState<string>()
  const [shortage, setShortage] = useState<Shortage>()
  const queue = usePartyQueueStatus(data?.raceId ?? 0, data?.raceCourseId ?? 0, queued && !shortage)
  const applied = useRef(false)

  const course = race.data?.courses?.find((c) => c.raceCourseId === data?.raceCourseId)
  const fail = (error: unknown) => {
    setQueued(false)
    applied.current = false
    if (isCrewSeatShortage(error)) {
      const info = (error as ApiError).body as { data?: Shortage }
      setShortage({ needed: info.data?.needed ?? 0, remaining: info.data?.remaining ?? 0 })
      return
    }
    setMessage(
      (error instanceof ApiError && error.serverMessage) ||
        '지금은 신청할 수 없어요. 잠시 후 다시 시도해 주세요.',
    )
  }

  const canEnter = queue.data?.canEnter === true
  useEffect(() => {
    if (!canEnter || applied.current) return
    applied.current = true
    apply.mutate(partyId, {
      onSuccess: () => router.replace(`/parties/${partyId}` as Route),
      onError: fail,
    })
    // fail · apply는 매 렌더 새로 만들어지지만 차례가 온 순간 한 번만 부르면 된다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canEnter])

  function start() {
    if (!data) return
    setMessage(undefined)
    register.mutate(
      { raceId: data.raceId, raceCourseId: data.raceCourseId },
      { onSuccess: () => setQueued(true), onError: fail },
    )
  }

  let body
  if (shortage) {
    body = (
      <>
        <EmptyState
          className="pt-12"
          role="alert"
          title={`크루 전용 자리가 ${Math.max(shortage.needed - shortage.remaining, 0)}자리 모자랐어요`}
          description="자리가 모자라면 파티 전체가 신청되지 않아요. 아무도 신청되지 않았고 결제한 돈도 없어요. 이 파티는 같은 대회에 다시 신청할 수 없어요."
        />
        <Divider />
        <section className="flex flex-col gap-3 bg-bg-surface px-4 py-6">
          <KeyValue label="신청 인원" value={`파티원 ${shortage.needed}명`} />
          <KeyValue label="남은 크루 전용 자리" value={`${shortage.remaining}자리`} />
          <KeyValue label="결제" value="청구된 금액 없음" />
        </section>
      </>
    )
  } else if (party.isPending) {
    body = (
      <div className="flex flex-col gap-3 p-4" aria-busy>
        <Skeleton className="h-8 w-2/3" />
        <Skeleton className="h-24 w-full" />
      </div>
    )
  } else if (!data) {
    body = <PartyLoadError onRetry={() => void party.refetch()} />
  } else {
    body = (
      <>
        <section className="flex flex-col gap-2 bg-bg-surface px-4 py-6">
          <h2 className="text-title1">{race.data?.title}</h2>
          <p className="text-body2-regular text-text-secondary">
            {crew.data ? `${crew.data.name} 크루 · ` : ''}파티원 {data.memberCount}명
          </p>
        </section>
        <Divider type="section" />
        <section className="flex flex-col gap-4 bg-bg-surface px-4 py-6">
          <h2 className="text-title2">{course?.name}로 잡을 자리</h2>
          <div className="flex items-center justify-between gap-3">
            <div className="flex flex-col gap-1">
              <span className="text-body1-medium">{course?.name}</span>
              <span className="text-caption-regular text-text-secondary">
                크루 전용 자리 {course?.crewRemaining ?? 0}개 남음
              </span>
            </div>
            <span className="text-body1-medium">{data.memberCount}명</span>
          </div>
          {message && <Alert tone="danger" title={message} />}
          {queued && (
            <ProgressBar
              indeterminate
              label="신청 차례를 기다리고 있어요"
              valueText={queue.data ? `내 앞 ${queue.data.ahead}명` : undefined}
            />
          )}
        </section>
        <Divider type="section" />
        <section className="flex flex-col gap-3 bg-bg-surface px-4 py-6">
          <KeyValue label="파티 신청 수수료" value="0원" />
          <KeyValue label="결제" value="파티원이 각자 결제" />
        </section>
      </>
    )
  }

  return (
    <>
      <AppHeader type="sub" title="파티 신청" />
      <main className="flex flex-1 flex-col">{body}</main>
      {data && !shortage && (
        <BottomCta
          className="sticky bottom-0 z-10"
          primary={
            <Button
              size="l"
              disabled={queued}
              loading={register.isPending || apply.isPending}
              onClick={start}
            >
              {queued ? '차례를 기다리는 중' : `${data.memberCount}명 파티 신청하기`}
            </Button>
          }
        />
      )}
    </>
  )
}
