'use client'

import type { ReactNode } from 'react'

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
  kakao: 'bg-[#FEE500]',
  google: 'border border-[#747775] bg-white',
}

// 피그마 v2 로그인: 지름 40 원형 아이콘 버튼 (카카오 말풍선, 구글 G)
const MARK: Record<SocialProviderId, ReactNode> = {
  kakao: (
    <svg viewBox="0 0 24 24" className="size-5" aria-hidden>
      <path
        fill="#191919"
        d="M12 3C6.48 3 2 6.58 2 10.99c0 2.85 1.86 5.35 4.67 6.77l-.95 3.48c-.08.3.26.54.52.37l4.15-2.76c.53.07 1.07.11 1.61.11 5.52 0 10-3.58 10-7.97C22 6.58 17.52 3 12 3z"
      />
    </svg>
  ),
  google: (
    <svg viewBox="0 0 48 48" className="size-5" aria-hidden>
      <path
        fill="#EA4335"
        d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"
      />
      <path
        fill="#4285F4"
        d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"
      />
      <path
        fill="#FBBC05"
        d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"
      />
      <path
        fill="#34A853"
        d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"
      />
    </svg>
  ),
}

/**
 * 카카오·구글로 시작하기 원형 버튼. fetch가 아니라 화면 이동으로 백엔드 시작 주소를 연다.
 */
export function SocialLoginButtons({ next }: { next?: string }) {
  return (
    <div className="flex justify-center gap-4">
      {SOCIAL_PROVIDERS.map((provider) => (
        <button
          key={provider.id}
          type="button"
          aria-label={`${provider.label}로 시작하기`}
          onClick={() => goToSocialLogin(socialAuthorizeHref(provider.id, { next }))}
          className={cn(
            'flex size-10 items-center justify-center rounded-full',
            STYLE[provider.id],
            FOCUS_RING,
          )}
        >
          {MARK[provider.id]}
        </button>
      ))}
    </div>
  )
}
