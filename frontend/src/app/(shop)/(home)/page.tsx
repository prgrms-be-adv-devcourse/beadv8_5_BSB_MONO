import { HomePage, NEW_RACES_PARAMS, pickNewRaces } from '@/views/home'
import {
  applicationPhase,
  fetchHomeBanners,
  fetchRacePage,
  RACE_PAGE_SIZE,
  RaceSort,
  toClosingSoonCard,
  toHomeBanner,
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
  const [closing, latest, banners] = await Promise.all([
    fetchRacePage({ sort: RaceSort.DEADLINE, page: 0, size: RACE_PAGE_SIZE }).catch(() => null),
    // 새로 등록된 대회를 못 받아도 홈은 그린다(그 영역만 숨긴다)
    fetchRacePage(NEW_RACES_PARAMS).catch(() => null),
    // 배너를 못 받아도 홈은 그린다(배너 영역만 숨긴다)
    fetchHomeBanners().catch(() => []),
  ])
  // 백엔드에 닿지 못하면 공통 연결 끊김 화면
  if (!closing) return <HomePage state="offline" closingSoon={[]} />

  const now = new Date()
  const closingSoon = (closing.races ?? [])
    .filter((race) => applicationPhase(race, now) === 'open')
    .slice(0, CLOSING_SOON)
    .map((race) => toClosingSoonCard(race, now))
  return (
    <HomePage
      banners={banners.map((race) => toHomeBanner(race, now))}
      closingSoon={closingSoon}
      newRaces={pickNewRaces(
        latest?.races ?? [],
        closingSoon.map((card) => card.id),
        now,
      )}
    />
  )
}
