import { NextResponse, type NextRequest } from 'next/server'

import { loginHref } from '@/features/auth/lib/login-redirect'
import { isProtectedPath } from '@/features/auth/lib/protected-path'
import { MOCK_LOGIN_MARK_COOKIE_NAME } from '@/shared/mocks/mock-session'

/**
 * 화면을 그리기 전에 하는 접근 확인.
 *
 * - 가짜 응답 모드(API_MOCKING=on): 로그인해야 하는 화면(isProtectedPath)을 비로그인으로 열면 로그인 화면으로 바로 보낸다.
 *   판단 근거는 가짜 로그인이 Path=/ 로 심는 표시 쿠키(mockLoggedIn)다. 보호 화면이 아예 그려지지 않는다.
 * - 실제 API 모드: 통과시킨다. Refresh Token 쿠키가 Path=/api/v1/auth(게이트웨이)라 화면 요청에 실리지 않아
 *   로그인 여부를 알 수 없다. 대신 브라우저의 ProtectedScreenGuard가 확인이 끝나기 전까지 내용을 그리지 않고,
 *   AuthSessionRestorer가 재발급 실패 시 /login으로 보낸다. 실제 권한 검사는 백엔드가 한다.
 * - /seller, /admin은 지금 모두 통과시킨다 (위와 같은 이유).
 */
export function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl
  const mocking = process.env.API_MOCKING === 'on' || process.env.NEXT_PUBLIC_API_MOCKING === 'on'
  if (mocking && isProtectedPath(pathname) && !request.cookies.has(MOCK_LOGIN_MARK_COOKIE_NAME)) {
    return NextResponse.redirect(new URL(loginHref(`${pathname}${search}`), request.url))
  }
  return NextResponse.next()
}

export const config = {
  // isProtectedPath(features/auth/lib/protected-path.ts)와 같은 범위. matcher는 정적 값이라 따로 적는다.
  matcher: [
    '/seller/:path*',
    '/admin/:path*',
    '/me/:path*',
    '/tickets/:path*',
    '/cart/:path*',
    '/checkout/:path*',
    '/notifications/:path*',
    '/crews/:path*',
    '/invite/:path*',
    '/races/:raceId/apply/:path*',
  ],
}
