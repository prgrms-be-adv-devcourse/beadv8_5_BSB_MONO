'use client'

import { useEffect } from 'react'

import { rememberAppStart } from './go-back'

/** 앱을 처음 그릴 때 기록 길이와 경로를 남긴다. 화면에는 아무것도 그리지 않는다. */
export function InAppHistoryTracker() {
  useEffect(() => rememberAppStart(), [])
  return null
}
