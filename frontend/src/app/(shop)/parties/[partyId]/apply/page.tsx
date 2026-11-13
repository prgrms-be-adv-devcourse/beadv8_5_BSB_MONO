import { notFound } from 'next/navigation'

import { PartyApplyPage } from '@/views/party'

export default async function Page({ params }: PageProps<'/parties/[partyId]/apply'>) {
  const id = Number((await params).partyId)
  if (!Number.isInteger(id) || id <= 0) notFound()
  return <PartyApplyPage partyId={id} />
}
