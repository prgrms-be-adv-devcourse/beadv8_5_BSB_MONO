'use client'

import { cn } from '@/shared/lib'
import { FOCUS_RING } from '@/shared/ui'

import {
  goToSocialLogin,
  SOCIAL_PROVIDERS,
  socialAuthorizeHref,
  type SocialProviderId,
} from '../lib/social-login'

// 버튼 색은 각 제공자 로그인 디자인 가이드를 따른다 (카카오 노란 바탕 #FEE500, 구글 흰 바탕 + 테두리).
// 앱 검수가 가이드 색을 보므로 디자인 토큰 대신 가이드 값을 쓴다.
const STYLE: Record<SocialProviderId, string> = {
  kakao: 'bg-[#FEE500] text-black/85',
  google: 'border border-[#747775] bg-white text-[#1F1F1F]',
}

/**
 * 카카오·구글로 시작하기 버튼. fetch가 아니라 화면 이동으로 백엔드 시작 주소를 연다.
 * highlight는 이메일 로그인이 「소셜로 가입한 계정」이라고 거절했을 때 그 제공자를 강조한다.
 */
export function SocialLoginButtons({
  next,
  highlight,
}: {
  next?: string
  highlight?: SocialProviderId
}) {
  return (
    <div className="flex w-full flex-col gap-2">
      {SOCIAL_PROVIDERS.map((provider) => (
        <button
          key={provider.id}
          type="button"
          onClick={() => goToSocialLogin(socialAuthorizeHref(provider.id, { next }))}
          className={cn(
            'flex h-12 w-full items-center justify-center rounded-md text-label-m font-bold',
            STYLE[provider.id],
            highlight === provider.id && 'ring-2 ring-border-brand ring-offset-2',
            FOCUS_RING,
          )}
        >
          {provider.label}로 시작하기
        </button>
      ))}
    </div>
  )
}
