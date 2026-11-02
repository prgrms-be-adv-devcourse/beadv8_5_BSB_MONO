import { notFound } from 'next/navigation'

import { CrewInvitesPage } from '@/views/crew'

export default async function Page({ params }: PageProps<'/crews/[crewId]/invites'>) {
  const crewId = Number((await params).crewId)
  if (!Number.isInteger(crewId) || crewId <= 0) notFound()
  return <CrewInvitesPage crewId={crewId} />
}
