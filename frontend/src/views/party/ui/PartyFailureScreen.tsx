import type { Route } from 'next'
import Link from 'next/link'

import { Button, buttonClassName, EmptyState, Logo } from '@/shared/ui'

import type { FailureCopy } from '../lib/party-copy'

const ACTION = {
  party: (_crewId?: number, partyId?: number) => ({
    href: partyId ? `/parties/${partyId}` : '/crews',
    label: partyId ? '파티 보기' : '내 크루로 가기',
  }),
  crew: (crewId?: number) => ({
    href: crewId ? `/crews/${crewId}` : '/crews',
    label: '내 크루로 가기',
  }),
  crews: () => ({ href: '/', label: '홈으로 가기' }),
  races: () => ({ href: '/parties/new', label: '다른 대회 고르기' }),
} as const

/**
 * 합류 · 만들기가 막혔을 때 한 가지 이유와 다음 행동 하나만 보여 준다.
 * 시안 파티 09~14 · 25 · 26: 가로 128px 회색 크루런 글자 로고, 회색 제목과 설명, 다음 행동 버튼 순서.
 */
export function PartyFailureScreen({
  copy,
  crewId,
  partyId,
}: {
  copy: FailureCopy
  crewId?: number
  partyId?: number
}) {
  const { href, label } = ACTION[copy.action](crewId, partyId)
  return (
    <div className="flex w-full flex-1 flex-col items-center justify-center gap-6 px-6 pb-14 text-center">
      <Logo variant="wordmarkMuted" decorative className="h-auto w-32" />
      <div role="alert" className="flex flex-col gap-2">
        <p className="text-title3 text-text-primary">{copy.title}</p>
        {copy.description && (
          <p className="text-caption-regular text-text-tertiary">{copy.description}</p>
        )}
      </div>
      <Link href={href as Route} className={buttonClassName({ variant: 'secondary', size: 'm' })}>
        {label}
      </Link>
    </div>
  )
}

export function PartyLoadError({ onRetry }: { onRetry: () => void }) {
  return (
    <EmptyState
      className="pt-26"
      icon={null}
      role="alert"
      title="파티를 불러오지 못했어요"
      action={
        <Button variant="secondary" onClick={onRetry}>
          다시 불러오기
        </Button>
      }
    />
  )
}
