'use client'

import { useEffect, useRef } from 'react'

import { clientEnv } from '@/shared/config'

import { failPath, type ChargeQuery } from '../lib/app-charge'

/**
 * 앱 안 브라우저에서 열려 토스 결제창만 연다. 로그인도 회원 API도 쓰지 않는다.
 * 충전 요청(주문번호·금액)은 앱이 로그인 상태에서 이미 만들어 쿼리로 넘겼다.
 * 결제 성공·실패는 토스가 /app-charge/done, /app-charge/fail로 보내고, 거기서 앱으로 돌려보낸다.
 */
export function AppChargeStart({
  query,
  navigate = (url) => window.location.replace(url),
}: {
  query: ChargeQuery | undefined
  navigate?: (url: string) => void
}) {
  const started = useRef(false)

  useEffect(() => {
    if (started.current) return
    started.current = true
    const fail = (code: string, message: string) =>
      navigate(failPath(code, message, query?.orderId))

    if (!query) return fail('INVALID_REQUEST', '충전 요청이 올바르지 않아요')
    const clientKey = clientEnv.NEXT_PUBLIC_TOSS_CLIENT_KEY
    if (!clientKey) return fail('CLIENT_KEY_MISSING', '결제창을 열 수 없어요')

    void (async () => {
      let toss
      try {
        const { loadTossPayments } = await import('@tosspayments/tosspayments-sdk')
        toss = await loadTossPayments(clientKey)
      } catch {
        return fail('SDK_LOAD_FAILED', '결제창을 불러오지 못했어요')
      }
      try {
        await toss.payment({ customerKey: query.customerKey }).requestPayment({
          method: 'CARD',
          amount: { currency: 'KRW', value: query.amount },
          orderId: query.orderId,
          orderName: query.orderName,
          successUrl: `${window.location.origin}/app-charge/done`,
          failUrl: `${window.location.origin}/app-charge/fail`,
        })
      } catch (error) {
        // 결제창을 닫으면 토스가 실패 주소로 보내지 않고 여기로 던진다
        const { code, message } = (error ?? {}) as { code?: unknown; message?: unknown }
        fail(
          typeof code === 'string' ? code : 'PAYMENT_FAILED',
          typeof message === 'string' ? message : '결제를 마치지 못했어요',
        )
      }
    })()
  }, [query, navigate])

  // 결제 화면에서 뒤로 가기로 되살아나면 위 effect가 다시 돌지 않아 화면이 멈춘다. 결제를 마치지 않은 것으로 앱에 돌려보낸다.
  useEffect(() => {
    const onPageShow = (event: PageTransitionEvent) => {
      if (event.persisted)
        navigate(failPath('PAYMENT_ABORTED', '결제를 마치지 못했어요', query?.orderId))
    }
    window.addEventListener('pageshow', onPageShow)
    return () => window.removeEventListener('pageshow', onPageShow)
  }, [query, navigate])

  return <p className="p-6 text-center">결제창을 여는 중이에요</p>
}
