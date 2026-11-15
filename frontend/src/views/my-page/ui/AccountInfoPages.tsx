'use client'

import Link from 'next/link'

import { AppHeader } from '@/widgets/app-header'
import { useAuthStatus } from '@/features/auth'
import { memberApi, unwrapData } from '@/shared/api'
import { cn } from '@/shared/lib'
import { FOCUS_RING, Icon, TextField } from '@/shared/ui'

import { EMPTY } from './MenuRow'

// 피그마 개선 화면 모음 · 이메일 정보(1333:21355): 가입한 이메일을 보기만 한다
export function EmailInfoPage() {
  const me = memberApi.useFindMe({
    query: { select: unwrapData, enabled: useAuthStatus() === 'authenticated' },
  })
  return (
    <>
      <AppHeader type="sub" title="이메일 정보" backHref="/me/settings" />
      <main className="flex flex-1 flex-col px-4 py-6" data-clarity-mask="true">
        <TextField label="계정 이메일" value={me.data?.email ?? EMPTY} disabled readOnly />
      </main>
    </>
  )
}

// 피그마 개선 화면 모음 · 약관 및 동의(1333:21464): 약관 전문은 가입 때와 같은 화면(/terms/...)을 연다
// TODO(api): 시안의 「마케팅 정보 받기 · 동의한 날」은 회원 정보에 동의 이력이 없어 날짜를 두지 않았다.
const REQUIRED = [
  { href: '/terms/service', label: '서비스 이용약관' },
  { href: '/terms/privacy', label: '개인정보 수집 · 이용' },
] as const

export function TermsConsentPage() {
  return (
    <>
      <AppHeader type="sub" title="약관 및 동의" backHref="/me/settings" />
      <main className="flex flex-1 flex-col gap-6 px-4 py-6">
        <TermsGroup title="필수 동의 약관" items={REQUIRED} />
        <TermsGroup
          title="선택 동의"
          items={[{ href: '/terms/marketing', label: '마케팅 정보 받기' }]}
        />
      </main>
    </>
  )
}

function TermsGroup({
  title,
  items,
}: {
  title: string
  items: readonly {
    href: '/terms/service' | '/terms/privacy' | '/terms/marketing'
    label: string
  }[]
}) {
  return (
    <section className="flex flex-col">
      <h2 className="pb-1 text-body2-bold">{title}</h2>
      {items.map((item) => (
        <Link
          key={item.href}
          href={item.href}
          className={cn('flex items-center gap-3 rounded-md py-3', FOCUS_RING)}
        >
          <span className="flex-1 text-body1-regular">{item.label}</span>
          <Icon name="ChevronRight" className="size-5 shrink-0 text-text-tertiary" />
        </Link>
      ))}
    </section>
  )
}
