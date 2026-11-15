import type { Route } from 'next'
import Link from 'next/link'

import { buttonClassName, EmptyState, Logo } from '@/shared/ui'

import type { FailureCopy } from '../lib/party-copy'

const ACTION = {
  crew: (crewId?: number) => ({
    href: crewId ? `/crews/${crewId}` : '/crews',
    label: '크루 홈으로 가기',
  }),
  crews: () => ({ href: '/', label: '홈으로 가기' }),
  races: () => ({ href: '/parties/new', label: '다른 대회 고르기' }),
} as const

/**
 * 합류 · 만들기가 막혔을 때 한 가지 이유와 다음 행동 하나만 보여 준다.
 * 시안 파티 09~14 · 25 · 26: 가로 128px 회색 크루런 글자 로고, 회색 제목과 설명, 다음 행동 버튼 순서.
 */
export function PartyFailureScreen({ copy, crewId }: { copy: FailureCopy; crewId?: number }) {
  const { href, label } = ACTION[copy.action](crewId)
  return (
    <div className="flex w-full flex-col items-center gap-4 px-6 pt-26 text-center">
      <Logo variant="wordmarkMuted" decorative className="h-auto w-32" />
      <div role="alert" className="flex flex-col">
        <p className="text-body1-medium text-text-secondary">{copy.title}</p>
        <p className="text-body2-regular text-text-tertiary">{copy.description}</p>
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
      role="alert"
      title="파티 정보를 불러오지 못했어요"
      description="잠시 후 다시 시도해 주세요."
      action={
        <button
          type="button"
          className={buttonClassName({ variant: 'secondary', size: 'm' })}
          onClick={onRetry}
        >
          다시 불러오기
        </button>
      }
    />
  )
}
