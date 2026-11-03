import { clientEnv } from '@/shared/config'

import { isAdminPath } from './track'

type ClarityQueue = ((...args: unknown[]) => void) & { q?: unknown[][] }

declare global {
  interface Window {
    clarity?: ClarityQueue
  }
}

/**
 * Clarity(화면 녹화 · 히트맵)를 넣을지. GTM과 같은 조건이다.
 * Clarity ID가 없거나 가짜 응답 모드면 넣지 않아 로컬 · 테스트 · Vercel 시안 화면이 녹화에 섞이지 않게 한다.
 */
export function trackingClarityId(): string | undefined {
  if (clientEnv.NEXT_PUBLIC_API_MOCKING === 'on') return undefined
  return clientEnv.NEXT_PUBLIC_CLARITY_ID
}

let stopped = false

/**
 * 화면 주소가 바뀔 때마다 부른다. 어드민은 크루런 직원 화면이고 회원 정보가 많아 녹화하지 않는다.
 * 어드민으로 처음 들어오면 스크립트를 넣지 않고, 구매자 화면에서 어드민으로 옮기면 기록을 멈췄다가 나오면 다시 시작한다.
 * 기본 가리기(입력 칸 · 숫자 · 이메일)는 Clarity 설정에서 끄지 않는다.
 * 스크립트가 막히거나 실패해도 화면 동작이 멈추지 않도록 예외를 밖으로 던지지 않는다.
 */
export function syncClarity(id: string, pathname: string) {
  try {
    const admin = isAdminPath(pathname)
    if (!window.clarity) {
      if (admin) return
      // Clarity 공식 설치 코드와 같다: 스크립트가 받아지기 전 호출은 q에 쌓아 둔다
      const clarity: ClarityQueue = (...args) => (clarity.q ??= []).push(args)
      window.clarity = clarity
      const script = document.createElement('script')
      script.async = true
      script.src = `https://www.clarity.ms/tag/${encodeURIComponent(id)}`
      document.head.appendChild(script)
      return
    }
    // stop · start는 Clarity 스크립트(clarity-js)가 내보내는 함수다. 공식 문서 표에는 없어 어드민 화면은 가리기도 함께 붙인다
    if (admin !== stopped) {
      stopped = admin
      window.clarity(admin ? 'stop' : 'start')
    }
  } catch (error) {
    if (process.env.NODE_ENV === 'development') console.warn('[analytics] Clarity 실행 실패', error)
  }
}
