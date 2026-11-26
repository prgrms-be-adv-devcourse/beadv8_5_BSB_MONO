import type { Metadata } from 'next'

import { appFailUrl, AppRedirect } from '@/views/app-charge'

export const metadata: Metadata = { title: '예치금 충전', robots: { index: false } }

// 토스 failUrl. ?code&message&orderId 를 앱 주소로 넘긴다.
export default async function Page({ searchParams }: PageProps<'/app-charge/fail'>) {
  return <AppRedirect url={appFailUrl(await searchParams)} />
}
