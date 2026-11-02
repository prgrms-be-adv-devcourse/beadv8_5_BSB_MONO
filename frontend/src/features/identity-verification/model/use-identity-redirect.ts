'use client'

import { useEffect, useRef } from 'react'

import {
  completeFromRedirect,
  type IdentityVerificationOutcome,
} from '../api/identity-verification'

/**
 * 모바일에서 포트원 창을 다녀와 가입 화면으로 돌아왔으면 결과를 받는다. 처음 그릴 때 한 번만 본다.
 * 결과를 받으면 주소의 쿼리(identityVerificationId 등)를 지워, 새로고침해도 다시 확인하지 않게 한다.
 */
export function useIdentityRedirect(onOutcome: (outcome: IdentityVerificationOutcome) => void) {
  const handled = useRef(false)
  const callback = useRef(onOutcome)
  useEffect(() => {
    callback.current = onOutcome
  })
  useEffect(() => {
    if (handled.current) return
    handled.current = true
    const url = new URL(window.location.href)
    void completeFromRedirect(url.searchParams).then((outcome) => {
      if (!outcome) return
      for (const key of [
        'identityVerificationId',
        'identityVerificationTxId',
        'transactionType',
        'code',
        'message',
        'pgCode',
        'pgMessage',
      ]) {
        url.searchParams.delete(key)
      }
      window.history.replaceState(window.history.state, '', url)
      callback.current(outcome)
    })
  }, [])
}
