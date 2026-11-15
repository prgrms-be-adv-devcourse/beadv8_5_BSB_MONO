import { PartyNewPage } from '@/views/party'

// 파티 만들기. 크루 홈의 「파티 만들기」가 ?crewId=로 크루를 넘긴다.
export default async function Page({ searchParams }: PageProps<'/parties/new'>) {
  const { crewId } = await searchParams
  const id = Number(crewId)
  return <PartyNewPage crewId={Number.isInteger(id) && id > 0 ? id : undefined} />
}
