import { LoginPage } from '@/views/auth'

// ?preview=error 로 M03-E(로그인 실패)를 볼 수 있다
// ?next=<경로> 는 로그인 뒤 돌아갈 화면 (앱 내부 경로만 쓰고 아니면 홈)
// ?error=&provider=&joinedWith= 는 소셜 로그인 콜백이 붙인 결과
export default async function Page({ searchParams }: PageProps<'/login'>) {
  const { preview, next, error, provider, joinedWith } = await searchParams
  return (
    <LoginPage
      failed={preview === 'error'}
      next={text(next)}
      social={{ error: text(error), provider: text(provider), joinedWith: text(joinedWith) }}
    />
  )
}

function text(value: string | string[] | undefined): string | undefined {
  return typeof value === 'string' ? value : undefined
}
