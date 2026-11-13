'use client'

import { GoogleTagManager } from '@next/third-parties/google'

import { trackingGtmId, useReportAppError } from '@/shared/lib'

// 루트 레이아웃까지 실패했을 때만 보인다. 루트 레이아웃을 대신하므로 html · body를 직접 그리고,
// 전역 CSS가 없어 style로 꾸민다. GTM도 다시 넣어 app_error가 GA4로 가게 한다.
export default function GlobalError({
  error,
  retry,
}: {
  error: Error & { digest?: string }
  retry: () => void
}) {
  useReportAppError(error)
  const gtmId = trackingGtmId()
  return (
    <html lang="ko">
      {gtmId && <GoogleTagManager gtmId={gtmId} />}
      <body
        style={{
          margin: 0,
          minHeight: '100dvh',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 16,
          fontFamily: 'system-ui, sans-serif',
          textAlign: 'center',
        }}
      >
        <h1 style={{ fontSize: 20, margin: 0 }}>문제가 생겼어요</h1>
        <p style={{ margin: 0, color: '#6b7280' }}>잠시 후 다시 시도해 주세요.</p>
        <button
          type="button"
          onClick={() => retry()}
          style={{
            padding: '12px 24px',
            borderRadius: 8,
            border: '1px solid #d1d5db',
            background: '#fff',
            fontSize: 16,
          }}
        >
          다시 시도하기
        </button>
      </body>
    </html>
  )
}
