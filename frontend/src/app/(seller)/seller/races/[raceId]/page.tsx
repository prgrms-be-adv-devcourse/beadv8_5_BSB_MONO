import { notFound } from 'next/navigation'

import { SellerRaceDetailPage } from '@/views/seller-races'

export default async function Page({ params }: PageProps<'/seller/races/[raceId]'>) {
  const id = Number((await params).raceId)
  if (!Number.isInteger(id) || id <= 0) notFound()
  return <SellerRaceDetailPage raceId={id} />
}
