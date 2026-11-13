import { notFound } from 'next/navigation'

import { PartyDetailPage } from '@/views/party'

export default async function Page({ params }: PageProps<'/parties/[partyId]'>) {
  const id = Number((await params).partyId)
  if (!Number.isInteger(id) || id <= 0) notFound()
  return <PartyDetailPage partyId={id} />
}
