import { SignupAccountPage } from '@/views/auth'

// 본인인증 다음의 계정(이메일 · 비밀번호) 단계. ?preview=error 로 M04-1E(입력 오류)를 볼 수 있다
export default async function Page({ searchParams }: PageProps<'/signup/account'>) {
  const { preview } = await searchParams
  return <SignupAccountPage invalid={preview === 'error'} />
}
