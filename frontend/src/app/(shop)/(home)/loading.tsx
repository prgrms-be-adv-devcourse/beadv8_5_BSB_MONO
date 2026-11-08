import { HomePage } from '@/views/home'

// 피그마 시나리오 38 · 01 홈 로딩: 헤더와 하단 탭은 바로, 본문 회색 틀은 0.3초 뒤에 보인다
export default function Loading() {
  return <HomePage state="loading" closingSoon={[]} openRaces={[]} />
}
