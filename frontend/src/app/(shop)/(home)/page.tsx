import { HomePage } from '@/views/home'
import {
  applicationPhase,
  fetchRacePage,
  RACE_PAGE_SIZE,
  RaceSort,
  toClosingSoonCard,
} from '@/entities/race'

/** 곧 마감: 마감 임박순 대회 4개 (2열) */
const CLOSING_SOON = 4

// ?preview=loading|offline 로 홈 로딩 · 공통 연결 끊김 화면을 볼 수 있다
export default async function Page({ searchParams }: PageProps<'/'>) {
  const { preview } = await searchParams
  if (preview === 'loading' || preview === 'offline') {
    return <HomePage state={preview} closingSoon={[]} />
  }

  // 마감 임박순 목록에는 아직 신청을 열지 않은 대회도 섞여 오므로, 한 페이지를 받아 지금 접수 중인 대회만 고른다
  const closing = await fetchRacePage({
    sort: RaceSort.DEADLINE,
    page: 0,
    size: RACE_PAGE_SIZE,
  }).catch(() => null)
  // 백엔드에 닿지 못하면 공통 연결 끊김 화면
  if (!closing) return <HomePage state="offline" closingSoon={[]} />

  const now = new Date()
  return (
    <HomePage
      closingSoon={(closing.races ?? [])
        .filter((race) => applicationPhase(race, now) === 'open')
        .slice(0, CLOSING_SOON)
        .map((race) => toClosingSoonCard(race, now))}
    />
  )
}
