import { applicationPhase, RaceSort, toNewRaceCard, type RaceSummary } from '@/entities/race'
import type { productModel } from '@/shared/api'

/** 새로 등록된 대회: 2열 카드 4개 */
const NEW_RACES_COUNT = 4

/** 최신 등록순 첫 페이지. 곧 마감돼요와 겹치는 대회를 빼고도 4개가 남도록 4개를 더 받는다 */
export const NEW_RACES_PARAMS = { sort: RaceSort.LATEST, page: 0, size: NEW_RACES_COUNT * 2 }

/** 서버가 준 순서(최신 등록순)대로 곧 마감 카드와 겹치지 않는 4개를 카드로 만든다 */
export function pickNewRaces(
  races: productModel.RaceSummaryDto[],
  closingSoonIds: string[],
  now: Date,
): RaceSummary[] {
  return races
    .filter(
      (race) =>
        !closingSoonIds.includes(String(race.raceId)) && applicationPhase(race, now) !== 'closed',
    )
    .slice(0, NEW_RACES_COUNT)
    .map((race) => toNewRaceCard(race, now))
}
