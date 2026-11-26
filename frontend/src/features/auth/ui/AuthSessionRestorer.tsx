'use client'

import { useQueryClient } from '@tanstack/react-query'
import { useRouter } from 'next/navigation'
import { useEffect } from 'react'

import { getAuthStatus, memberApi, onSessionExpired, restoreSession } from '@/shared/api'

import { loginHref } from '../lib/login-redirect'

/**
 * 앱 전체에 한 번 둔다 (화면에는 아무것도 그리지 않는다).
 * - 새로고침하면 메모리의 Access Token이 사라지므로 재발급으로 되살리고, 성공하면 내 정보를 불러 둔다.
 * - 쓰는 중에 재발급이 실패해 로그인이 끊기면 로그인 화면으로 보낸다. 로그인 뒤 보던 화면으로 돌아오게 지금 주소를 넘긴다.
 */
export function AuthSessionRestorer() {
  const queryClient = useQueryClient()
  const router = useRouter()

  useEffect(() => {
    // 앱 안 브라우저로 여는 충전 페이지는 로그인이 필요 없다. 기기 브라우저와 쿠키를 같이 쓰면 재발급이 웹 로그인의 토큰을 바꾸므로 부르지 않는다.
    if (window.location.pathname.startsWith('/app-charge')) return
    void restoreSession().then(() => {
      if (getAuthStatus() === 'authenticated') {
        void queryClient.prefetchQuery(memberApi.getFindMeQueryOptions())
      }
    })
  }, [queryClient])

  useEffect(
    () =>
      onSessionExpired(() => {
        const { pathname, search } = window.location
        router.replace(loginHref(`${pathname}${search}`))
      }),
    [router],
  )

  return null
}
