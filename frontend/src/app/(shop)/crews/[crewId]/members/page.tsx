import { notFound } from 'next/navigation'

import { CrewMembersPage } from '@/views/crew'

export default async function Page({ params }: PageProps<'/crews/[crewId]/members'>) {
  const crewId = Number((await params).crewId)
  if (!Number.isInteger(crewId) || crewId <= 0) notFound()
  return <CrewMembersPage crewId={crewId} />
}
