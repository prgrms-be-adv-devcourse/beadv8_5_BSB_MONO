import { PartyJoinPage } from '@/views/party'

// 파티 참가 링크. 토큰은 서버 로그에 남지 않게 요청 본문으로만 보낸다.
export default async function Page({ params }: PageProps<'/parties/join/[token]'>) {
  const { token } = await params
  return <PartyJoinPage token={token} />
}
