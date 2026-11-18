// @vitest-environment node
import { expect, it } from 'vitest'

import { toRoi } from './roi'

const shown = { width: 400, height: 300 }
const natural = { width: 4000, height: 3000 }

it('화면 좌표를 원본 픽셀로 늘린다(거꾸로 끌어도 같다)', () => {
  expect(toRoi({ x: 300, y: 200 }, { x: 100, y: 50 }, shown, natural)).toEqual({
    x: 1000,
    y: 500,
    width: 2000,
    height: 1500,
  })
})

it('사진 밖은 자른다', () => {
  expect(toRoi({ x: -20, y: -20 }, { x: 500, y: 100 }, shown, natural)).toEqual({
    x: 0,
    y: 0,
    width: 4000,
    height: 1000,
  })
})

it('너무 작으면 사진 전체', () => {
  expect(toRoi({ x: 10, y: 10 }, { x: 11, y: 11 }, shown, natural)).toEqual({
    x: 0,
    y: 0,
    width: 4000,
    height: 3000,
  })
})
