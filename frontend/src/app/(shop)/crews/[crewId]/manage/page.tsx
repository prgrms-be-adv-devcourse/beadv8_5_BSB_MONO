import { notFound } from 'next/navigation'

import { CrewManagePage } from '@/views/crew'

export default async function Page({ params }: PageProps<'/crews/[crewId]/manage'>) {
  const crewId = Number((await params).crewId)
  if (!Number.isInteger(crewId) || crewId <= 0) notFound()
  return <CrewManagePage crewId={crewId} />
}
