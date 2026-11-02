import { notFound } from 'next/navigation'

import { CrewHomePage } from '@/views/crew'

export default async function Page({ params }: PageProps<'/crews/[crewId]'>) {
  const crewId = Number((await params).crewId)
  if (!Number.isInteger(crewId) || crewId <= 0) notFound()
  return <CrewHomePage crewId={crewId} />
}
