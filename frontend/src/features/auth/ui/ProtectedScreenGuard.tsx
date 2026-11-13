'use client'

import { usePathname, useRouter } from 'next/navigation'
import { useEffect, type ReactNode } from 'react'

import { ProgressBar } from '@/shared/ui'

import { loginHref } from '../lib/login-redirect'
import { isProtectedPath } from '../lib/protected-path'
import { useAuthStatus } from '../model/use-auth-status'

/** 하단 탭(widgets/bottom-nav)과 홈 바로가기(views/home)가 여는 회원 화면 */
const NAV_ENTRY_PATHS = new Set(['/tickets', '/cart', '/me', '/me/deposit', '/crews'])

/**
 * 로그인해야 하는 화면(isProtectedPath)은 로그인 확인(재발급)이 끝나기 전에는 내용을 그리지 않고
 * 가운데 로딩만 보여 준다. 비로그인으로 열면 로그인 화면으로 보낸다.
 * 회원 정보 뼈대가 잠깐 보였다가 로그인 화면으로 바뀌는 깜빡임을 막기 위해서다.
 *
 * 가짜 응답 모드는 proxy.ts가 이 화면에 오기 전에 비로그인을 먼저 돌려보낸다(로그인 표시 쿠키).
 * 실제 API 모드는 서버가 로그인 여부를 알 수 없어(Refresh Token 쿠키가 게이트웨이 경로에만 실린다) 이 가드가 맡는다.
 * 그 사이에 로그인이 끊기면(재발급 실패) AuthSessionRestorer도 같은 로그인 화면으로 보낸다.
 */
export function ProtectedScreenGuard({ children }: { children: ReactNode }) {
  const router = useRouter()
  const pathname = usePathname()
  const status = useAuthStatus()
  const guarded = isProtectedPath(pathname)

  useEffect(() => {
    if (guarded && status === 'anonymous') {
      // 하단 탭·홈 바로가기로 들어온 화면은 로그인 뒤 그 화면 대신 홈으로 보낸다 (next 없이 로그인하면 홈)
      router.replace(
        NAV_ENTRY_PATHS.has(pathname)
          ? '/login'
          : loginHref(`${pathname}${window.location.search}`),
      )
    }
  }, [guarded, status, pathname, router])

  if (guarded && status !== 'authenticated') {
    return (
      <main className="flex flex-1 items-center justify-center px-4">
        <ProgressBar indeterminate aria-label="로그인 확인 중" className="w-40" />
      </main>
    )
  }
  return children
}
