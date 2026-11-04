import { SocialCompletePage } from '@/views/auth'

// 소셜 로그인 완료. 백엔드 콜백이 Refresh Token 쿠키를 심고 이 화면으로 보낸다
// ?next=<경로> 는 로그인 뒤 돌아갈 화면
export default async function Page({ searchParams }: PageProps<'/auth/social/complete'>) {
  const { next } = await searchParams
  return <SocialCompletePage next={typeof next === 'string' ? next : undefined} />
}
