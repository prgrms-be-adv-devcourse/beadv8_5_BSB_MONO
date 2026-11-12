import { afterEach, describe, expect, it } from 'vitest'

import { forgetKitOptionIds, readKitOptionIds, saveKitOptionIds } from './kit-choice'

afterEach(() => sessionStorage.clear())

describe('주문별 고른 키트 옵션', () => {
  it('저장한 키트 옵션 ID를 같은 주문에서 다시 읽고, 다른 주문에는 보이지 않는다', () => {
    saveKitOptionIds(7, [11, 22])

    expect(readKitOptionIds(7)).toEqual([11, 22])
    expect(readKitOptionIds(8)).toEqual([])
  })

  it('지우면 읽히지 않는다', () => {
    saveKitOptionIds(7, [11])
    forgetKitOptionIds(7)

    expect(readKitOptionIds(7)).toEqual([])
  })

  it('저장된 값이 깨져 있으면 옵션 없음이 아니라 빈 목록으로 읽는다', () => {
    sessionStorage.setItem('crewrun:kit-options:7', '{깨짐')

    expect(readKitOptionIds(7)).toEqual([])
  })
})
