import { notFound } from 'next/navigation'

import { CrewInvitesPage } from '@/views/crew'

export default async function Page({ params, searchParams }: PageProps<'/crews/[crewId]/invites'>) {
  const crewId = Number((await params).crewId)
  if (!Number.isInteger(crewId) || crewId <= 0) notFound()
  // 카카오 메시지 동의 화면에서 돌아올 때 백엔드가 붙이는 결과 (?kakaoConsent=granted, ?error=)
  const { kakaoConsent, error } = await searchParams
  return (
    <CrewInvitesPage
      crewId={crewId}
      kakaoConsent={typeof kakaoConsent === 'string' ? kakaoConsent : undefined}
      kakaoError={typeof error === 'string' ? error : undefined}
    />
  )
}
