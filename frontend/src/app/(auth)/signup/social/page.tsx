import { SocialSignupPage } from '@/views/auth'

// 소셜로 처음 온 사람의 가입. 백엔드 소셜 로그인 콜백이 가입 대기 쿠키를 심고 이 화면으로 보낸다
// ?next=<경로> 는 가입 뒤 돌아갈 화면
export default async function Page({ searchParams }: PageProps<'/signup/social'>) {
  const { next } = await searchParams
  return <SocialSignupPage next={typeof next === 'string' ? next : undefined} />
}
