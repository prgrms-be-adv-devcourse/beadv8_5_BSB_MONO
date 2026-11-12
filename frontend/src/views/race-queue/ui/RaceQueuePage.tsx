'use client'

import { useQuery } from '@tanstack/react-query'
import type { Route } from 'next'
import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { loginHref } from '@/features/auth'
import { skippedNotice, useAdmissionTimer, useQueuePosition } from '@/features/queue-admission'
import { raceDetailQueryOptions } from '@/entities/race'
import {
  BottomCta,
  buttonClassName,
  Dialog,
  formatTimer,
  Icon,
  IconButton,
  ProgressBar,
} from '@/shared/ui'

import { useQueueTracking } from '../model/use-queue-tracking'

// 피그마 V2 · 대기열 A안(확정) · 나가기 확인 / M05-Q2 대기열 · 내 차례
// 같은 주소에서 입장 여부에 따라 두 화면을 바꿔 그린다.
// 들어오면 대기열에 등록하고(useQueuePosition) 차례가 올 때까지 순번을 짧은 간격마다 다시 묻는다.
// 차례가 오면 주문 서비스에 입장 확인을 묻고, 입장하면 버튼을 누르지 않아도 신청서(M05)로 넘어간다.
// 넘어가는 동안에는 기다리는 화면을 그대로 둔다. 내 차례 화면(M05-Q2)을 그리면 남은 시간이 잠깐 깜빡이기 때문이다.
// 내 차례 화면은 시안 미리보기에서만 그린다. 남은 시간은 신청서 위쪽 띠에서 보여 준다.
// 남은 시간은 신청서에서 1초씩 줄여 가고, 0이 되면 입장 시간 만료 화면(M07-1)으로 간다.
// A안은 대기 인원과 내 순서 숫자를 보이지 않는다. 오른쪽 위 X를 누르면 나가기 확인 창이 뜨고, 나가면 대회 상세로 간다.
// 상단 timer bar 띠는 이 화면에 없다(신청서 M05부터).
// 96px 그림도 시안처럼 선을 2px로 둔다. 아이콘은 24 격자라 96px에서는 0.5가 2px이다.
const BIG_ICON_STROKE = 0.5

export function RaceQueuePage({
  raceId,
  optionId,
  previewRemainingSeconds,
}: {
  raceId: string
  /** 줄을 선 종목. 입장 여부를 묻고, 신청 화면(M05)으로 넘길 때 쓴다 */
  optionId: string
  /** 시안 미리보기(?preview=admitted). 있으면 API를 부르지 않고 이 남은 초로 내 차례를 보여 준다 */
  previewRemainingSeconds?: number
}) {
  const preview = previewRemainingSeconds !== undefined
  const queue = useQueuePosition(
    preview ? undefined : Number(raceId),
    preview ? undefined : Number(optionId),
  )
  const timer = useAdmissionTimer(
    preview ? undefined : Number(raceId),
    preview ? undefined : Number(optionId),
    'wait',
    queue.canEnter,
  )
  const remaining = preview ? previewRemainingSeconds : timer.remaining

  // 로그인이 필요하면(401) 로그인 화면으로 보낸다. 로그인 뒤 이 대기열로 돌아오게 지금 주소를 넘긴다.
  const router = useRouter()
  const pathname = usePathname()
  const unauthorized = timer.errorStatus === 401 || queue.unauthorized
  useEffect(() => {
    if (unauthorized) router.replace(loginHref(`${pathname}${window.location.search}`))
  }, [unauthorized, pathname, router])

  const raceHref = `/races/${raceId}` as Route
  const race = useQuery(raceDetailQueryOptions(raceId))
  const optionName = race.data?.options.find((o) => o.id === optionId)?.name
  const [leaving, setLeaving] = useState(false)
  const applyHref = `/races/${raceId}/apply?option=${encodeURIComponent(optionId)}` as const

  // 차례가 오면 신청서로 자동으로 넘어간다 (시안 미리보기는 머문다)
  const admitted = remaining !== null
  useQueueTracking(raceId, admitted, !preview)
  useEffect(() => {
    if (admitted && !preview) router.replace(applyHref)
  }, [admitted, preview, applyHref, router])

  // 대기열에 등록하지 못했다. 입장 확인은 묻지 않고 이유를 안내한다
  const blocked = !preview && queue.skippedStatus !== undefined
  const notice = skippedNotice(queue.skippedStatus ?? 0)

  return (
    <>
      <AppHeader type="sub" title="대기열" close backHref={raceHref} />
      {blocked ? (
        // 줄을 서지 못했다. 배경에 대기 화면을 그리지 않고, 확인하면 대회 상세로 돌려보낸다
        <>
          <main className="flex-1" />
          <Dialog
            open
            type="alert"
            title={notice.title}
            body={notice.description}
            confirmLabel="대회로 돌아가기"
            onConfirm={() => router.replace(raceHref)}
            onOpenChange={() => router.replace(raceHref)}
          />
        </>
      ) : queue.failed && remaining === null ? (
        <>
          <main className="flex flex-1 flex-col items-center justify-center gap-8 px-4 py-6">
            <h1 className="text-center text-title2 text-text-primary">
              대기열에서 자리를 잃었어요
              <br />
              다시 줄을 서 주세요
            </h1>
            <Icon
              name="Users"
              className="size-24 text-text-primary"
              strokeWidth={BIG_ICON_STROKE}
              aria-hidden
            />
            <p className="text-center text-body2-regular text-text-secondary">
              대기열이 다시 시작되어 순서가 사라졌어요.
              <br />
              다시 줄을 서면 처음부터 기다려요.
            </p>
          </main>
          <BottomCta
            className="sticky bottom-0 z-10"
            primary={
              <button
                type="button"
                onClick={queue.retry}
                className={buttonClassName({ size: 'l' })}
              >
                다시 줄 서기
              </button>
            }
          />
        </>
      ) : preview && remaining !== null ? (
        <>
          <main className="flex flex-1 flex-col items-center justify-center gap-8 px-4 py-6">
            <h1 className="text-center text-title2 text-text-primary">
              내 차례예요
              <br />
              지금 신청할 수 있어요
            </h1>
            <Icon
              name="CircleCheck"
              className="size-24 text-text-primary"
              strokeWidth={BIG_ICON_STROKE}
              aria-hidden
            />
            <div role="timer" className="flex flex-col items-center gap-1 text-center">
              <p className="text-body2-medium text-text-secondary">남은 시간</p>
              <p className="font-display text-number-s text-text-primary tabular-nums">
                {formatTimer(remaining)}
              </p>
            </div>
            <p className="text-center text-body2-regular text-text-secondary">
              남은 시간 안에 참가 신청과 결제를 마쳐 주세요.
              <br />
              시간이 지나면 다시 줄을 서야 해요.
            </p>
          </main>
          <BottomCta
            className="sticky bottom-0 z-10"
            primary={
              <Link href={applyHref} className={buttonClassName({ size: 'l' })}>
                신청하러 가기
              </Link>
            }
          />
        </>
      ) : (
        <>
          <header className="flex h-(--layout-header-height) shrink-0 items-center justify-end px-1">
            <IconButton aria-label="닫기" onClick={() => setLeaving(true)}>
              <Icon name="X" />
            </IconButton>
          </header>
          <main className="flex flex-1 flex-col items-center px-6 pt-6 text-center">
            <h1 className="text-title2 text-text-primary">
              신청하는 분이 많아
              <br />
              잠시 기다리고 있어요
            </h1>
            {race.data && (
              <p className="pt-2 text-body2-regular text-text-secondary">
                {optionName ? `${race.data.title} · ${optionName}` : race.data.title}
              </p>
            )}
            <Icon
              name="Users"
              className="mt-9 size-24 text-text-primary"
              strokeWidth={BIG_ICON_STROKE}
              aria-hidden
            />
            {/* 진행률이 아니라 기다리는 중이라는 표시라 값 없이 계속 움직인다 */}
            <ProgressBar indeterminate aria-label="대기 중" className="mt-9 w-full" />
            <p className="pt-9 text-caption-regular text-text-tertiary">
              이 화면을 나가면 순서가 처음으로 돌아가요.
            </p>
          </main>
          <Dialog
            open={leaving}
            onOpenChange={(open) => !open && setLeaving(false)}
            onCancel={() => router.replace(raceHref)}
            title="대기열에서 나갈까요?"
            body="지금 나가면 내 대기 순서가 없어져요. 다시 신청하면 맨 뒤에서 기다려야 해요."
            cancelLabel="나가기"
            confirmLabel="계속 기다리기"
            onConfirm={() => setLeaving(false)}
          />
        </>
      )}
    </>
  )
}
