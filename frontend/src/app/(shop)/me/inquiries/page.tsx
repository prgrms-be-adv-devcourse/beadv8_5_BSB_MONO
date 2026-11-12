import { MyInquiriesPage } from '@/views/inquiry'

// 문의를 보내거나 지운 직후에 ?sent=1 · ?deleted=1로 온다
export default async function Page({ searchParams }: PageProps<'/me/inquiries'>) {
  const { sent, deleted } = await searchParams
  const notice = sent ? '문의를 보냈어요' : deleted ? '문의를 지웠어요' : undefined
  return <MyInquiriesPage notice={notice} />
}
