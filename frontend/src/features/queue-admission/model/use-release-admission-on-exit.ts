'use client'

import { useEffect, useRef } from 'react'

import { queueApi } from '@/shared/api'

export type AdmissionTarget = {
  line: 'individual' | 'party'
  raceId: number
  raceCourseId: number
}

// 입장을 이어 가는 화면. 이 안에서 옮겨 다닐 때는 같은 입장이라 해제하지 않는다.
// 개인: 신청서 → 결제. 파티: 파티 신청 화면 하나.
const FLOW_PATHS: Record<AdmissionTarget['line'], RegExp> = {
  individual: /^\/(races\/\d+\/apply|checkout)\/?$/,
  party: /^\/parties\/\d+\/apply\/?$/,
}

/**
 * 대기열 입장(active) 해제. 응답은 기다리지 않고, 실패해도 화면에 영향이 없다.
 * 탭을 닫는 중에도 요청이 끝나도록 keepalive로 보낸다. Access Token이 필요해 sendBeacon은 쓸 수 없다.
 */
function release({ line, raceId, raceCourseId }: AdmissionTarget) {
  const send = line === 'party' ? queueApi.releasePartyActive : queueApi.releaseActive
  void send(raceId, raceCourseId, { keepalive: true }).catch(() => {})
}

/**
 * 신청서 · 결제 · 파티 신청 화면에서 대기열 입장을 이어 가는 동안 붙여 두는 훅.
 * 이 화면을 벗어나면(앱 안 이동 · 뒤로 가기 · 탭 닫기 · 새로고침) 입장을 풀어서, 다시 들어올 때 줄을 새로 서게 한다.
 *
 * - target이 없으면 아무것도 하지 않는다 (시안 미리보기, 종목을 아직 모를 때).
 * - keep이 true면 pagehide에도 풀지 않는다. 토스 결제창으로 떠났다가 같은 입장으로 돌아와야 할 때 쓴다.
 * - 앱 안 이동은 화면이 내려갈 때 새 주소를 보고 정한다. Next는 새 화면을 그린 뒤 옛 화면을 내리므로
 *   그 시점의 주소가 이동할 곳이다. 같은 입장 안의 화면(신청서 ↔ 결제)이면 풀지 않는다.
 */
export function useReleaseAdmissionOnExit(
  target: AdmissionTarget | undefined,
  { keep = false }: { keep?: boolean } = {},
) {
  const keepRef = useRef(keep)
  useEffect(() => {
    keepRef.current = keep
  })

  const line = target?.line
  const raceId = target?.raceId
  const raceCourseId = target?.raceCourseId

  useEffect(() => {
    if (!line || !raceId || !raceCourseId) return
    const current: AdmissionTarget = { line, raceId, raceCourseId }
    const onPageHide = () => {
      if (!keepRef.current) release(current)
    }
    window.addEventListener('pagehide', onPageHide)
    return () => {
      window.removeEventListener('pagehide', onPageHide)
      if (!FLOW_PATHS[line].test(window.location.pathname)) release(current)
    }
  }, [line, raceId, raceCourseId])
}
