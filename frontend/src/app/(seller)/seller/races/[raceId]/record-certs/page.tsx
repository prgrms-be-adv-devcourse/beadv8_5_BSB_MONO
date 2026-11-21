import { notFound } from 'next/navigation'

import { SellerRecordReviewPage } from '@/views/seller-record-review'

export default async function Page({ params }: PageProps<'/seller/races/[raceId]/record-certs'>) {
  const id = Number((await params).raceId)
  if (!Number.isInteger(id) || id <= 0) notFound()
  return <SellerRecordReviewPage raceId={id} />
}
