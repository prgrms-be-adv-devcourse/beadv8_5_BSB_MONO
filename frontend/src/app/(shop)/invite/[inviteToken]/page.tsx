import { CrewInvitePage } from '@/views/crew'

// 크루 초대 링크(M10). 크루장 · 운영진이 초대 링크 만들기(M27)에서 복사해 보낸 주소다.
// ?party=가 있으면 파티 참가 토큰이다. 크루 가입 뒤 그 파티 합류 화면으로 이어진다(BUK-98).
export default async function Page({ params, searchParams }: PageProps<'/invite/[inviteToken]'>) {
  const { inviteToken } = await params
  const { party } = await searchParams
  return (
    <CrewInvitePage
      inviteToken={inviteToken}
      partyJoinToken={typeof party === 'string' && party ? party : undefined}
    />
  )
}
