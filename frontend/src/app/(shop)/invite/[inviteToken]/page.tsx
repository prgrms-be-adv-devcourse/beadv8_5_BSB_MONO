import { CrewInvitePage } from '@/views/crew'

// 크루 초대 링크(M10). 크루장 · 운영진이 초대 링크 만들기(M27)에서 복사해 보낸 주소다.
export default async function Page({ params }: PageProps<'/invite/[inviteToken]'>) {
  const { inviteToken } = await params
  return <CrewInvitePage inviteToken={inviteToken} />
}
