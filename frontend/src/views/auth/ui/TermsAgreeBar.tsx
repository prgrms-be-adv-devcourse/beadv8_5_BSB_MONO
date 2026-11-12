'use client'

import { useRouter, useSearchParams } from 'next/navigation'

import { BottomCta, Button } from '@/shared/ui'

import { useSignupStep } from '../model/signup-store'
import type { TermsSlug } from '../model/terms'

// 시안 약관 전문 아래 「동의하기」. 가입 약관 시트에서 열었을 때(?from=signup)만 보인다.
// 누르면 그 약관을 체크하고 시트가 있던 화면으로 돌아간다. 로그인 화면 등 다른 곳에서 열면 조회 전용이다.
export function TermsAgreeBar({ slug }: { slug: TermsSlug }) {
  const from = useSearchParams().get('from')
  return from === 'signup' ? <AgreeButton slug={slug} /> : null
}

function AgreeButton({ slug }: { slug: TermsSlug }) {
  const router = useRouter()
  const setTerm = useSignupStep((s) => s.setTerm)
  return (
    <BottomCta
      className="sticky bottom-0 z-10"
      primary={
        <Button
          size="l"
          onClick={() => {
            setTerm(slug, true)
            router.back()
          }}
        >
          동의하기
        </Button>
      }
    />
  )
}
