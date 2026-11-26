import type { Metadata } from 'next'

import { AppChargeStart, parseChargeQuery } from '@/views/app-charge'

export const metadata: Metadata = { title: '예치금 충전', robots: { index: false } }

// 모바일 앱의 앱 안 브라우저가 연다. ?orderId&amount&orderName&customerKey 를 받아 토스 결제창만 연다.
export default async function Page({ searchParams }: PageProps<'/app-charge'>) {
  return <AppChargeStart query={parseChargeQuery(await searchParams)} />
}
