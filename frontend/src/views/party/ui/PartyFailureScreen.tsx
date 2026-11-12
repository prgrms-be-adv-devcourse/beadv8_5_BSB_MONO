import type { Route } from 'next'
import Link from 'next/link'

import { buttonClassName, EmptyState } from '@/shared/ui'

import type { FailureCopy } from '../lib/party-copy'

const ACTION = {
  crew: (crewId?: number) => ({
    href: crewId ? `/crews/${crewId}` : '/crews',
    label: '크루 홈으로 가기',
  }),
  crews: () => ({ href: '/crews', label: '내 크루로 가기' }),
  races: () => ({ href: '/parties/new', label: '다른 대회 고르기' }),
} as const

/** 합류 · 만들기가 막혔을 때 한 가지 이유와 다음 행동 하나만 보여 준다. */
export function PartyFailureScreen({ copy, crewId }: { copy: FailureCopy; crewId?: number }) {
  const { href, label } = ACTION[copy.action](crewId)
  return (
    <EmptyState
      className="pt-26"
      role="alert"
      title={copy.title}
      description={copy.description}
      action={
        <Link href={href as Route} className={buttonClassName({ variant: 'secondary', size: 'm' })}>
          {label}
        </Link>
      }
    />
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
