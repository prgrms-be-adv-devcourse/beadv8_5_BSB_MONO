'use client'

import { useEffect, useRef } from 'react'

import { trackEvent, type AnalyticsEventName, type AnalyticsEvents } from './track'

/**
 * 화면이 보일 때 이벤트를 한 번 보낸다. 값을 아직 모르면(불러오는 중) null을 넘기고, 값이 생긴 첫 렌더에 보낸다.
 * 개발 모드에서 effect가 두 번 돌아도 한 번만 보낸다.
 */
export function useTrackOnce<K extends AnalyticsEventName>(
  name: K,
  params: AnalyticsEvents[K] | null,
) {
  const sent = useRef(false)
  useEffect(() => {
    if (sent.current || params === null) return
    sent.current = true
    trackEvent(name, params)
  })
}
