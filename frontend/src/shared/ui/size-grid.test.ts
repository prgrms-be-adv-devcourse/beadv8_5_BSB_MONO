import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

// 크기 값은 4의 배수(예외는 선 굵기뿐). 4의 배수가 아니라고 지적받은 클래스가 다시 들어오지 않게 소스에서 확인한다.
const SRC = join(__dirname, '..', '..')
const FORBIDDEN: [file: string, pattern: RegExp][] = [
  ['views/auth/ui/LoginPage.tsx', /h-\[86px\]|w-\[142\.5px\]/],
  ['views/party/ui/PartyDetailPage.tsx', /\bmt-3\.5\b|\bgap-2\.5\b|\bpt-4\.5\b/],
  ['views/my-page/ui/MyPage.tsx', /\bh-33\.5\b/],
  ['shared/ui/progress-bar/ProgressBar.tsx', /\bh-1\.5\b/],
  ['views/seller-settlements/ui/SellerSettlementsPage.tsx', /\bw-32\.5\b/],
  ['views/home/ui/HomePage.tsx', /\bh-3\.5\b|\bh-2\.5\b/],
  ['views/race-list/ui/RaceListPage.tsx', /\bpt-2\.5\b/],
]

describe('크기 값은 4의 배수', () => {
  it.each(FORBIDDEN)('%s에 4의 배수가 아닌 크기가 없다', (file, pattern) => {
    expect(readFileSync(join(SRC, file), 'utf8')).not.toMatch(pattern)
  })
})
