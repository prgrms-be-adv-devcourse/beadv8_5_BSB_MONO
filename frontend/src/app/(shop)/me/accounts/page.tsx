import { AccountLinkPage } from '@/views/my-page'

// 계정 연결. 소셜 계정 연결을 마치면 백엔드가 ?linked= 또는 ?error=를 붙여 이 화면으로 보낸다
export default async function Page({ searchParams }: PageProps<'/me/accounts'>) {
  const { linked, error } = await searchParams
  return (
    <AccountLinkPage
      linked={typeof linked === 'string' ? linked : undefined}
      error={typeof error === 'string' ? error : undefined}
    />
  )
}
