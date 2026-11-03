import type { MetadataRoute } from 'next'

import { fetchRacePage } from '@/entities/race'
import { siteUrl } from '@/shared/config'

// 빌드할 때는 백엔드에 닿지 않으므로, 요청마다 대회 목록을 새로 받는다
export const dynamic = 'force-dynamic'

const PAGE_SIZE = 100
// shortcut: 대회 1,000개까지만 넣는다. 더 많아지면 generateSitemaps로 파일을 나눈다
const MAX_PAGES = 10

async function onSaleRaceIds() {
  const ids: number[] = []
  for (let page = 0; page < MAX_PAGES; page++) {
    const { races = [], totalPages = 0 } = await fetchRacePage({ page, size: PAGE_SIZE })
    ids.push(
      ...races.flatMap((race) =>
        race.saleStatus === 'ON_SALE' && race.raceId !== undefined ? [race.raceId] : [],
      ),
    )
    if (page + 1 >= totalPages) break
  }
  return ids
}

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const base = siteUrl()
  // 상품 서비스가 실패해도 홈 · 대회 목록 주소는 내보낸다
  const raceIds = await onSaleRaceIds().catch(() => [])
  return [
    { url: base, changeFrequency: 'daily' },
    { url: `${base}/races`, changeFrequency: 'daily' },
    ...raceIds.map((id) => ({ url: `${base}/races/${id}` })),
  ]
}
