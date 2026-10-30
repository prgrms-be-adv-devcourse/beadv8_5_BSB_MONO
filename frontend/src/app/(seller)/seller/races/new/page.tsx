import { SellerRaceNewPage } from '@/views/seller-race-new'
import type { SellerRaceFormEntry } from '@/shared/lib'

// ?from=home|races|sales: 어느 버튼으로 들어왔는지. 사용자 활동 추적(seller_race_form_start)에만 쓴다
const ENTRIES: SellerRaceFormEntry[] = ['home', 'races', 'sales']

export default async function Page({ searchParams }: PageProps<'/seller/races/new'>) {
  const { from } = await searchParams
  const entry = ENTRIES.find((e) => e === from) ?? 'direct'
  return <SellerRaceNewPage entry={entry} />
}
