'use client'

import type { Route } from 'next'
import { useRouter } from 'next/navigation'

// 앱을 처음 그릴 때의 기록 길이와 경로. replace는 기록 길이를 늘리지 않으므로, 처음 연 화면을 replace로 옮긴 경우는 앱 안 기록으로 세지 않는다.
let start: { length: number; path: string } | null = null

/** 앱을 처음 그릴 때 한 번 부른다(InAppHistoryTracker). */
export function rememberAppStart() {
  start ??= { length: window.history.length, path: window.location.pathname }
}

/** 테스트용: 앱 안에서 push로 옮겨 온 상태로 만든다. */
export function markInAppHistory() {
  start = { length: 0, path: '' }
}

/** 테스트용: 처음 상태로 되돌린다. */
export function resetInAppHistory() {
  start = null
}

// Navigation API의 canGoBack은 바로 앞 기록이 같은 출처일 때만 true라서 가장 정확하다.
// shortcut: 없는 브라우저는 기록 길이로 추정한다. 처음 화면을 replace한 뒤 push했다가 돌아온 경우는 앱 밖으로 나갈 수 있다. 지원 브라우저가 충분히 늘면 지운다.
function canGoBackInApp() {
  const nav = (window as { navigation?: { canGoBack: boolean } }).navigation
  if (nav) return nav.canGoBack
  return (
    start !== null &&
    window.history.length > start.length &&
    window.location.pathname !== start.path
  )
}

/** 앱 안에서 온 기록이 있으면 브라우저 뒤로, 없으면(바로 열었거나 새로고침) fallback으로 바꿔 간다. 앱 밖으로 나가지 않게 한다. */
export function useGoBack() {
  const router = useRouter()
  return (fallback: Route | string) => {
    if (canGoBackInApp()) router.back()
    else router.replace(fallback as Route)
  }
}
