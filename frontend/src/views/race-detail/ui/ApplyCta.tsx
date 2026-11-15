'use client'

import type { Route } from 'next'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useState, type ReactNode } from 'react'

import { loginHref, useAuthStatus } from '@/features/auth'
import { type PartyRaceStatus } from '@/entities/party'
import { formatRaceDateTime, parseKstDateTime, type RaceDetail } from '@/entities/race'
import {
  BottomCta,
  BottomSheet,
  BottomSheetOption,
  Button,
  buttonClassName,
  Icon,
} from '@/shared/ui'

export type ApplyCtaProps = {
  race: RaceDetail
  optionId: string
  alreadyApplied: boolean
  /** M02-4: 파티 결제를 안 했거나 실패한 파티원은 같은 대회에 다시 신청할 수 없다 */
  reapplyBlocked: boolean
  /** 이 대회 안에서의 내 파티 상태 (로그인했고 크루 전용 자리가 있는 대회만) */
  party?: PartyRaceStatus
}

// 하단 신청 버튼: 상태별 문구(M02-1 · M02-4 · M02-5)와 신청 방법 바텀시트(M02-3-1)
export function ApplyCta({ race, optionId, alreadyApplied, reapplyBlocked, party }: ApplyCtaProps) {
  const authStatus = useAuthStatus()
  const queueHref = `/races/${race.id}/queue?option=${optionId}` as const

  return <BottomCta className="sticky bottom-0 z-10" primary={primary()} />

  function primary(): ReactNode {
    if (alreadyApplied || (party?.role === 'MEMBER' && party.partyStatus === 'PAID')) {
      return disabled('이미 신청한 대회예요')
    }
    if (reapplyBlocked) return disabled('다시 신청할 수 없는 대회예요')
    if (race.phase !== 'open') {
      return disabled(race.phase === 'upcoming' ? '아직 접수 전이에요' : '접수가 마감됐어요')
    }
    if (optionId === '') {
      return disabled(
        race.options.length === 0 ? '신청할 수 있는 종목이 없어요' : '정원이 모두 찼어요',
      )
    }
    // 로그인 전이면 로그인(M03)으로 보내고, 로그인 뒤 대기열로 이어 간다. 시트 없이 바로 간다(⑨-1)
    if (authStatus !== 'authenticated' || !race.crew) {
      return (
        <Link
          href={authStatus === 'anonymous' ? loginHref(queueHref) : queueHref}
          className={buttonClassName({ size: 'l' })}
        >
          신청하기
        </Link>
      )
    }
    if (party?.role === 'MEMBER') {
      // 결제 기한을 놓치면 참가가 확정되지 않아서 하단 버튼에 바로 보여 준다(④)
      if (party.partyStatus === 'PAYING' && !party.myPaid && party.paymentDueAt) {
        return (
          <Link
            href={`/parties/${party.partyId}` as Route}
            className={buttonClassName({ size: 'l' })}
          >
            결제하기 · {formatRaceDateTime(party.paymentDueAt)}까지
          </Link>
        )
      }
      return disabled('파티로 신청 중이에요')
    }
    return <HowToApply race={race} queueHref={queueHref} party={party} />
  }
}

function disabled(label: string) {
  return (
    <Button size="l" disabled>
      {label}
    </Button>
  )
}

function HowToApply({
  race,
  queueHref,
  party,
}: {
  race: RaceDetail
  queueHref: string
  party?: PartyRaceStatus
}) {
  const router = useRouter()
  const [open, setOpen] = useState(false)
  const go = (href: string) => {
    setOpen(false)
    router.push(href as Route)
  }

  return (
    <>
      <Button size="l" onClick={() => setOpen(true)}>
        신청하기
      </Button>
      <BottomSheet open={open} onOpenChange={setOpen} title="어떻게 신청할까요?">
        <SheetRow icon="User" onClick={() => go(queueHref)}>
          개인으로 신청
        </SheetRow>
        {party && <PartyRow race={race} party={party} go={go} />}
      </BottomSheet>
    </>
  )
}

// 시트의 파티 항목. 판단은 파티 상태 API(role · canApply · reason)와 크루 예매 기간으로 한다
function PartyRow({
  race,
  party,
  go,
}: {
  race: RaceDetail
  party: PartyRaceStatus
  go: (href: string) => void
}) {
  const now = new Date()
  const { startAt, endAt } = race.crew ?? {}
  const outOfWindow =
    (!!startAt && now < parseKstDateTime(startAt)) || (!!endAt && now >= parseKstDateTime(endAt))

  if (party.role === 'NONE') {
    // ⑧ 크루 예매 기간 밖: 누를 수 없다. ⑦ 파티 없음: 크루 홈에서 만든다
    return outOfWindow ? (
      <SheetRow icon="Users" disabled>
        파티로 신청
      </SheetRow>
    ) : (
      <SheetRow icon="Users" onClick={() => go('/crews')}>
        크루 홈에서 파티 만들기
      </SheetRow>
    )
  }

  const course = race.options.find((o) => o.id === String(party.raceCourseId))?.name
  const people = `${course ? `${course} · ` : ''}${party.memberCount ?? 0}명`
  if (party.canApply) {
    return (
      <SheetRow icon="Users" sub={people} onClick={() => go(`/parties/${party.partyId}/apply`)}>
        파티로 신청
      </SheetRow>
    )
  }
  if (party.reason === 'TOO_FEW_MEMBERS') {
    // ② 인원 부족: 누를 수 없고 몇 명이 더 필요한지만 알린다
    const lack = (party.minCount ?? 0) - (party.memberCount ?? 0)
    return (
      <SheetRow icon="Users" sub={`${lack}명 더 모이면 신청할 수 있어요`} disabled>
        파티로 신청
      </SheetRow>
    )
  }
  // ⑥ 파티장 · 신청 후 등: 내 파티로 간다
  return (
    <SheetRow
      icon="Users"
      sub={`결제 ${party.paidCount ?? 0}/${party.memberCount ?? 0}명`}
      onClick={() => go(`/parties/${party.partyId}`)}
    >
      내 파티 보기
    </SheetRow>
  )
}

function SheetRow({
  icon,
  sub,
  children,
  ...props
}: {
  icon: 'User' | 'Users'
  sub?: string
  children: ReactNode
  disabled?: boolean
  onClick?: () => void
}) {
  return (
    <BottomSheetOption {...props}>
      <span className="flex items-center gap-3">
        <Icon name={icon} className="size-5 shrink-0" />
        <span className="flex min-w-0 flex-1 flex-col">
          {children}
          {sub && <span className="text-caption-regular text-text-tertiary">{sub}</span>}
        </span>
        {!props.disabled && <Icon name="ChevronRight" className="size-5 shrink-0" />}
      </span>
    </BottomSheetOption>
  )
}
