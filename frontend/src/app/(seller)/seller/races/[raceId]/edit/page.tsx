import { notFound } from 'next/navigation'

import { SellerRaceEditPage } from '@/views/seller-races'

export default async function Page({ params }: PageProps<'/seller/races/[raceId]/edit'>) {
  const id = Number((await params).raceId)
  if (!Number.isInteger(id) || id <= 0) notFound()
  return <SellerRaceEditPage raceId={id} />
}
