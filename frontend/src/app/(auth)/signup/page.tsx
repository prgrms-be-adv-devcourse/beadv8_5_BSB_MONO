import { SignupIdentityPage } from '@/views/auth'

// 가입의 첫 화면. 약관 동의(M04-0) 바텀시트가 먼저 뜨고, 그 아래가 본인 확인(M04-P 1)이다
// ?next=<경로> 는 가입을 마친 뒤 돌아갈 화면 (로그인 화면에서 넘겨준다, 앱 내부 경로만 쓰고 아니면 홈)
export default async function Page({ searchParams }: PageProps<'/signup'>) {
  const { next } = await searchParams
  return <SignupIdentityPage next={typeof next === 'string' ? next : undefined} />
}
