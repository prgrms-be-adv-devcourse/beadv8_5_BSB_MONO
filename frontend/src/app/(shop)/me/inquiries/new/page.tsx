import { InquiryComposePage } from '@/views/inquiry'

// 대회 상세의 「이 대회에 문의하기」는 ?raceId=로, 고객센터의 참가 · 결제 문의는 raceId 없이 온다
export default async function Page({ searchParams }: PageProps<'/me/inquiries/new'>) {
  const { raceId } = await searchParams
  const id = typeof raceId === 'string' && /^[1-9]\d*$/.test(raceId) ? Number(raceId) : undefined
  return <InquiryComposePage raceId={id} />
}
