import type { RaceDetail } from '../model/types'

// 검색엔진에 대회를 알리는 schema.org Event. 값이 없는 키는 넣지 않는다.
export function raceJsonLd(race: RaceDetail) {
  const organizer = race.seller.company || race.seller.name
  return {
    '@context': 'https://schema.org',
    '@type': 'Event',
    name: race.title,
    // 백엔드 일시는 시간대 없는 한국 시간이라 +09:00을 붙인다. 날짜만 있으면 그대로 둔다
    ...(race.raceDate && {
      startDate: race.raceDate.length > 10 ? `${race.raceDate}+09:00` : race.raceDate,
    }),
    ...(race.place && { location: { '@type': 'Place', name: race.place } }),
    ...(race.images.length > 0 && { image: race.images }),
    ...(organizer && { organizer: { '@type': 'Organization', name: organizer } }),
  }
}

/** <script> 안에 넣을 JSON. <를 <로 바꿔 값에 든 </script>가 태그를 닫지 못하게 한다 */
export function serializeJsonLd(data: unknown) {
  return JSON.stringify(data).replace(/</g, '\\u003c')
}
