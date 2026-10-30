'use client'

import { useEffect, useRef } from 'react'

import { trackEvent } from '@/shared/lib'

/**
 * 대기열에 들어오면 queue_enter, 차례가 와서 신청서로 넘어가면 queue_pass,
 * 그 전에 화면을 떠나면(다른 화면으로 이동 · 탭 닫기) queue_leave를 보낸다. 기다린 시간은 초 단위다.
 * enabled가 false면(시안 미리보기) 보내지 않는다.
 */
export function useQueueTracking(raceId: string, admitted: boolean, enabled: boolean) {
  const startedAt = useRef(0)
  const ended = useRef(false)

  useEffect(() => {
    if (!enabled) return
    startedAt.current = Date.now()
    ended.current = false
    trackEvent('queue_enter', { raceId })
    const leave = () => {
      if (ended.current) return
      ended.current = true
      trackEvent('queue_leave', { raceId, waitSeconds: waited(startedAt.current) })
    }
    window.addEventListener('pagehide', leave)
    return () => {
      window.removeEventListener('pagehide', leave)
      leave()
    }
  }, [enabled, raceId])

  useEffect(() => {
    if (!enabled || !admitted || ended.current) return
    ended.current = true
    trackEvent('queue_pass', { raceId, waitSeconds: waited(startedAt.current) })
  }, [enabled, admitted, raceId])
}

function waited(startedAt: number) {
  return Math.round((Date.now() - startedAt) / 1000)
}
