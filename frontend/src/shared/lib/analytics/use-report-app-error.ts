import { useEffect } from 'react'

import { trackEvent } from './track'

/** 오류 경계가 뜰 때 콘솔에 원인을 남기고 GA4 app_error를 한 번 보낸다. digest만 보내 개인정보가 섞이지 않게 한다 */
export function useReportAppError(error: Error & { digest?: string }) {
  useEffect(() => {
    console.error(error)
    trackEvent('app_error', { digest: error.digest })
  }, [error])
}
