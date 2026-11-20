'use client'

import type { Route } from 'next'
import { useRouter } from 'next/navigation'

import { canGoBackInApp } from './go-back'

/** 앱 안에서 온 기록이 있으면 브라우저 뒤로, 없으면(바로 열었거나 새로고침) fallback으로 바꿔 간다. 앱 밖으로 나가지 않게 한다. */
export function useGoBack() {
  const router = useRouter()
  return (fallback: Route | string) => {
    if (canGoBackInApp()) router.back()
    else router.replace(fallback as Route)
  }
}
